package org.esa.beam.dataio;

import com.bc.ceres.core.ProgressMonitor;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.object.Attribute;
import ncsa.hdf.object.FileFormat;
import ncsa.hdf.object.h5.H5Group;
import ncsa.hdf.object.h5.H5ScalarDS;
import org.esa.beam.framework.dataio.AbstractProductReader;
import org.esa.beam.framework.dataio.ProductReaderPlugIn;
import org.esa.beam.framework.datamodel.*;
import org.esa.beam.util.ProductUtils;
import org.esa.beam.util.logging.BeamLogManager;
import org.geotools.referencing.CRS;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.logging.Level;

/**
 * Reader for Proba-V Synthesis products
 *
 * @author olafd
 */
public class ProbaVSynthesisProductReader extends AbstractProductReader {

    private int productWidth;
    private int productHeight;

    private File probavFile;

    /**
     * Constructs a new abstract product reader.
     *
     * @param readerPlugIn the reader plug-in which created this reader, can be <code>null</code> for internal reader
     *                     implementations
     */
    protected ProbaVSynthesisProductReader(ProductReaderPlugIn readerPlugIn) {
        super(readerPlugIn);
    }

    @Override
    protected Product readProductNodesImpl() throws IOException {
        final Object inputObject = getInput();
        probavFile = ProbaVSynthesisProductReaderPlugIn.getFileInput(inputObject);
        final String fileName = probavFile.getName();

        Product targetProduct = null;

        if (ProbaVSynthesisProductReaderPlugIn.isHdf5LibAvailable()) {
            FileFormat h5FileFormat = FileFormat.getFileFormat(FileFormat.FILE_TYPE_HDF5);
            FileFormat h5File = null;
            try {
                h5File = h5FileFormat.createInstance(probavFile.getAbsolutePath(), FileFormat.READ);
                h5File.open();

                final TreeNode rootNode = h5File.getRootNode();

                // check of which of the supported product types the input is:
                if (ProbaVSynthesisProductReaderPlugIn.isProbaSynthesisToaProduct(fileName) ||
                        ProbaVSynthesisProductReaderPlugIn.isProbaSynthesisTocProduct(fileName)) {
                    targetProduct = createTargetProductFromSynthesis(probavFile, rootNode);
                } else if (ProbaVSynthesisProductReaderPlugIn.isProbaSynthesisNdviProduct(fileName)) {
                    targetProduct = createTargetProductFromSynthesisNdvi(probavFile, rootNode);
                }
            } catch (Exception e) {
                throw new IOException("Failed to open file " + probavFile.getPath());
            } finally {
                if (h5File != null) {
                    try {
                        h5File.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return targetProduct;
    }

    @Override
    protected void readBandRasterDataImpl(int sourceOffsetX, int sourceOffsetY, int sourceWidth, int sourceHeight, int sourceStepX, int sourceStepY, Band destBand, int destOffsetX, int destOffsetY, int destWidth, int destHeight, ProductData destBuffer, ProgressMonitor pm) throws IOException {
        throw new IllegalStateException(String.format("No source to read for band '%s'.", destBand.getName()));
    }

    //////////// private methods //////////////////

    private Product createTargetProductFromSynthesis(File inputFile, TreeNode inputFileRootNode) throws Exception {
        Product product = null;

        if (inputFileRootNode != null) {

            final TreeNode level3Node = inputFileRootNode.getChildAt(0);        // 'LEVEL3'
            productWidth = (int) getH5ScalarDS(level3Node.getChildAt(0).getChildAt(0)).getDims()[0];
            productHeight = (int) getH5ScalarDS(level3Node.getChildAt(0).getChildAt(0)).getDims()[1];
            product = new Product(inputFile.getName(), "PROBA-V SYNTHESIS", productWidth, productHeight);
            product.setAutoGrouping("TOA_REFL:TOC_REFL:VAA:VZA");

            final H5Group rootGroup = (H5Group) ((DefaultMutableTreeNode) inputFileRootNode).getUserObject();
            final List rootMetadata = rootGroup.getMetadata();
            addSynthesisMetadataElement(rootMetadata, product, ProbaVConstants.MPH_NAME);
            product.setDescription(ProbaVUtils.getDescriptionFromAttributes(rootMetadata));
            product.setFileLocation(inputFile);

            for (int i = 0; i < level3Node.getChildCount(); i++) {
                // we have: 'GEOMETRY', 'NDVI', 'QUALITY', 'RADIOMETRY', 'TIME'
                final TreeNode level3ChildNode = level3Node.getChildAt(i);
                final String level3ChildNodeName = level3ChildNode.toString();

                switch (level3ChildNodeName) {
                    case "GEOMETRY":
                        setSynthesisGeoCoding(product, inputFileRootNode, level3ChildNode);
                        // 8-bit unsigned character
                        for (int j = 0; j < level3ChildNode.getChildCount(); j++) {
                            final TreeNode level3GeometryChildNode = level3ChildNode.getChildAt(j);
                            final String level3GeometryChildNodeName = level3GeometryChildNode.toString();
                            if (isSynthesisSunAngleDataNode(level3GeometryChildNodeName)) {
                                final H5ScalarDS sunAngleDS = getH5ScalarDS(level3GeometryChildNode);
                                final Band sunAngleBand = createTargetBand(product, sunAngleDS, level3GeometryChildNodeName,
                                                                           ProductData.TYPE_UINT8);
                                setBandUnitAndDescription(sunAngleDS, sunAngleBand);
                                sunAngleBand.setNoDataValue(ProbaVConstants.GEOMETRY_NO_DATA_VALUE);
                                sunAngleBand.setNoDataValueUsed(true);
                                final byte[] sunAngleData = (byte[]) sunAngleDS.getData();
                                final ProbaVRasterImage sunAngleImage = new ProbaVRasterImage(sunAngleBand, sunAngleData,
                                                                                          level3GeometryChildNodeName);
                                sunAngleBand.setSourceImage(sunAngleImage);
                            } else if (isSynthesisViewAngleGroupNode(level3GeometryChildNodeName)) {
                                for (int k = 0; k < level3GeometryChildNode.getChildCount(); k++) {
                                    final TreeNode level3GeometryViewAngleChildNode = level3GeometryChildNode.getChildAt(k);
                                    final H5ScalarDS geometryViewAngleDS = getH5ScalarDS(level3GeometryViewAngleChildNode);
                                    final String level3GeometryViewAngleChildNodeName =
                                            level3GeometryViewAngleChildNode.toString();
                                    final String viewAnglebandName = level3GeometryViewAngleChildNodeName + "_" +
                                            level3GeometryChildNodeName;
                                    final Band geometryViewAngleBand =
                                            createTargetBand(product,
                                                             geometryViewAngleDS,
                                                             viewAnglebandName,
                                                             ProductData.TYPE_UINT8);
                                    setBandUnitAndDescription(geometryViewAngleDS, geometryViewAngleBand);
                                    geometryViewAngleBand.setNoDataValue(ProbaVConstants.GEOMETRY_NO_DATA_VALUE);
                                    geometryViewAngleBand.setNoDataValueUsed(true);
                                    final byte[] geometryViewAngleData = (byte[]) geometryViewAngleDS.getData();
                                    final ProbaVRasterImage geometryViewAngleImage =
                                            new ProbaVRasterImage(geometryViewAngleBand, geometryViewAngleData,
                                                                  viewAnglebandName);
                                    geometryViewAngleBand.setSourceImage(geometryViewAngleImage);
                                }
                            }
                        }
                        break;

                    case "NDVI":
                        // 8-bit unsigned character
                        setNdviBand(product, level3ChildNode);
                        break;

                    case "QUALITY":
                        // 8-bit unsigned character
                        final H5ScalarDS qualityDS = getH5ScalarDS(level3ChildNode.getChildAt(0));
                        Product flagProduct = new Product("QUALITY", "flags", productWidth, productHeight);
                        ProductUtils.copyGeoCoding(product, flagProduct);
                        final Band smBand =
                                createTargetBand(flagProduct, qualityDS, ProbaVConstants.SM_BAND_NAME, ProductData.TYPE_UINT8);
                        setBandUnitAndDescription(qualityDS, smBand);
                        final byte[] qualityData = (byte[]) qualityDS.getData();
                        final ProbaVRasterImage image = new ProbaVRasterImage(smBand, qualityData, level3ChildNodeName);
                        smBand.setSourceImage(image);
                        attachSynthesisQualityFlagBand(product, flagProduct);

                        // metadata:
                        addQualityMetadata(product, (DefaultMutableTreeNode) level3ChildNode);

                        break;

                    case "RADIOMETRY":
                        // 16-bit integer
                        //  blue, nir, red, swir:
                        for (int j = 0; j < level3ChildNode.getChildCount(); j++) {
                            // we want the sequence BLUE, RED, NIR, SWIR, rather than original BLUE, NIR, RED, SWIR...
                            final int k = ProbaVConstants.RADIOMETRY_CHILD_INDEX[j];
                            final TreeNode level3RadiometryChildNode = level3ChildNode.getChildAt(k);
                            final H5ScalarDS radiometryDS = getH5ScalarDS(level3RadiometryChildNode.getChildAt(0));
                            final String level3RadiometryChildNodeName = level3RadiometryChildNode.toString();
                            final String radiometryBandPrePrefix =
                                    (ProbaVSynthesisProductReaderPlugIn.isProbaSynthesisToaProduct(inputFile.getName())) ? "TOA" : "TOC";
                            final String radiometryBandPrefix = radiometryBandPrePrefix + "_REFL_";
                            final Band radiometryBand = createTargetBand(product,
                                                                         radiometryDS,
                                                                         radiometryBandPrefix + level3RadiometryChildNodeName,
                                                                         ProductData.TYPE_INT16);
                            setBandUnitAndDescription(radiometryDS, radiometryBand);
                            setSpectralBandProperties(radiometryBand);
                            radiometryBand.setNoDataValue(ProbaVConstants.RADIOMETRY_NO_DATA_VALUE);
                            radiometryBand.setNoDataValueUsed(true);
                            final short[] radiometryData = (short[]) radiometryDS.getData();
                            final ProbaVRasterImage radiometryImage = new ProbaVRasterImage(radiometryBand, radiometryData);
                            radiometryBand.setSourceImage(radiometryImage);
                        }
                        break;

                    case "TIME":
                        final H5ScalarDS timeDS = getH5ScalarDS(level3ChildNode.getChildAt(0));
                        final Band timeBand;
                        ProbaVRasterImage timeImage;
                        if (ProbaVSynthesisProductReaderPlugIn.isProbaSynthesisS1ToaToc100mProduct(inputFile.getName())) {
                            // 8-bit unsigned character in this case
                            timeBand = createTargetBand(product, timeDS, "TIME", ProductData.TYPE_UINT8);
                            timeBand.setNoDataValue(ProbaVConstants.TIME_NO_DATA_VALUE_UINT8);
                            byte[] timeData = (byte[]) timeDS.getData();
                            timeImage = new ProbaVRasterImage(timeBand, timeData, level3ChildNodeName);
                        } else {
                            // 16-bit unsigned integer
                            timeBand = createTargetBand(product, timeDS, "TIME", ProductData.TYPE_UINT16);
                            timeBand.setNoDataValue(ProbaVConstants.TIME_NO_DATA_VALUE_UINT16);
                            short[] timeData = (short[]) timeDS.getData();
                            timeImage = new ProbaVRasterImage(timeBand, timeData);
                        }
                        setBandUnitAndDescription(timeDS, timeBand);
                        timeBand.setNoDataValueUsed(true);
                        timeBand.setSourceImage(timeImage);

                        // add start/end time to product:
                        addStartStopTimes(product, (DefaultMutableTreeNode) level3ChildNode);
                        break;

                    default:
                        break;
                }
            }
        }

        return product;
    }

    private Product createTargetProductFromSynthesisNdvi(File inputFile, TreeNode inputFileRootNode) throws Exception {

        Product product = null;
        if (inputFileRootNode != null) {
            final TreeNode level3Node = inputFileRootNode.getChildAt(0);        // 'LEVEL3'
            productWidth = (int) getH5ScalarDS(level3Node.getChildAt(1).getChildAt(0)).getDims()[0];
            productHeight = (int) getH5ScalarDS(level3Node.getChildAt(1).getChildAt(0)).getDims()[1];
            product = new Product(inputFile.getName(), "PROBA-V SYNTHESIS NDVI", productWidth, productHeight);

            final H5Group rootGroup = (H5Group) ((DefaultMutableTreeNode) inputFileRootNode).getUserObject();
            final List rootMetadata = rootGroup.getMetadata();
            addSynthesisMetadataElement(rootMetadata, product, ProbaVConstants.MPH_NAME);
            product.setDescription(ProbaVUtils.getDescriptionFromAttributes(rootMetadata));
            product.setFileLocation(inputFile);

            for (int i = 0; i < level3Node.getChildCount(); i++) {
                // we have: 'GEOMETRY', 'NDVI', 'QUALITY', 'TIME'
                final TreeNode level3ChildNode = level3Node.getChildAt(i);
                final String level3ChildNodeName = level3ChildNode.toString();

                switch (level3ChildNodeName) {
                    case "GEOMETRY":
                        setSynthesisGeoCoding(product, inputFileRootNode, level3ChildNode);
                        break;

                    case "NDVI":
                        // 8-bit unsigned character
                        setNdviBand(product, level3ChildNode);
                        break;

                    case "QUALITY":
                        addQualityMetadata(product, (DefaultMutableTreeNode) level3ChildNode);
                        break;

                    case "TIME":
                        // add start/end time to product:
                        addStartStopTimes(product, (DefaultMutableTreeNode) level3ChildNode);
                        break;

                    default:
                        break;
                }
            }
        }
        return product;
    }

    private void addStartStopTimes(Product product, DefaultMutableTreeNode level3ChildNode) throws HDF5Exception, ParseException {
        final H5Group timeGroup = (H5Group) level3ChildNode.getUserObject();
        final List timeMetadata = timeGroup.getMetadata();
        product.setStartTime(ProductData.UTC.parse(ProbaVUtils.getStartEndTimeFromAttributes(timeMetadata)[0],
                                                   ProbaVConstants.PROBAV_DATE_FORMAT_PATTERN));
        product.setEndTime(ProductData.UTC.parse(ProbaVUtils.getStartEndTimeFromAttributes(timeMetadata)[1],
                                                 ProbaVConstants.PROBAV_DATE_FORMAT_PATTERN));
    }

    private void addQualityMetadata(Product product, DefaultMutableTreeNode level3ChildNode) throws HDF5Exception {
        final H5Group qualityGroup = (H5Group) level3ChildNode.getUserObject();
        final List qualityMetadata = qualityGroup.getMetadata();
        addSynthesisMetadataElement(qualityMetadata, product, ProbaVConstants.QUALITY_NAME);
    }

    private void setNdviBand(Product product, TreeNode level3ChildNode) throws Exception {
        final H5ScalarDS ndviDS = getH5ScalarDS(level3ChildNode.getChildAt(0));
        final Band ndviBand = createTargetBand(product, ndviDS, "NDVI", ProductData.TYPE_FLOAT32);
        ndviBand.setDescription("Normalized Difference Vegetation Index");
        ndviBand.setUnit("dl");
        ndviBand.setNoDataValue(ProbaVConstants.NDVI_NO_DATA_VALUE);
        ndviBand.setNoDataValueUsed(true);
        final byte[] ndviData = (byte[]) ndviDS.getData();
        // the scaling does not work properly with the original data, convert to scaled floats manually
        final float[] ndviFloatData = ProbaVUtils.getNdviAsFloat(ndviBand, ndviData);
        final ProbaVRasterImage ndviImage = new ProbaVRasterImage(ndviBand, ndviFloatData);
        ndviBand.setSourceImage(ndviImage);
    }

    private void setBandUnitAndDescription(H5ScalarDS ds, Band band) throws HDF5Exception {
        band.setDescription(ProbaVUtils.getDescriptionFromAttributes(ds.getMetadata()));
        band.setUnit(ProbaVUtils.getUnitFromAttributes(ds.getMetadata()));
    }

    private void setSpectralBandProperties(Band band) {
        if (band.getName().endsWith("REFL_BLUE")) {
            band.setSpectralBandIndex(0);
            band.setSpectralWavelength(462.0f);
            band.setSpectralBandwidth(48.0f);
        } else if (band.getName().endsWith("REFL_RED")) {
            band.setSpectralBandIndex(1);
            band.setSpectralWavelength(655.5f);
            band.setSpectralBandwidth(81.0f);
        } else if (band.getName().endsWith("REFL_NIR")) {
            band.setSpectralBandIndex(2);
            band.setSpectralWavelength(843.0f);
            band.setSpectralBandwidth(142.0f);
        } else if (band.getName().endsWith("REFL_SWIR")) {
            band.setSpectralBandIndex(3);
            band.setSpectralWavelength(1599.0f);
            band.setSpectralBandwidth(70.0f);
        }
    }

    private void setSynthesisGeoCoding(Product product, TreeNode inputFileRootNode, TreeNode level3ChildNode)
            throws HDF5Exception {

        final H5Group h5GeometryGroup = (H5Group) ((DefaultMutableTreeNode) level3ChildNode).getUserObject();
        final List geometryMetadata = h5GeometryGroup.getMetadata();
        final double easting = ProbaVUtils.getGeometryCoordinateValueFromAttributes(geometryMetadata, "TOP_LEFT_LONGITUDE");
        final double northing = ProbaVUtils.getGeometryCoordinateValueFromAttributes(geometryMetadata, "TOP_LEFT_LATITUDE");
        // pixel size: 10deg/rasterDim, it is also in the 6th and 7th value of MAPPING attribute in the raster nodes
        final double topLeftLon = easting;
        final double topRightLon = ProbaVUtils.getGeometryCoordinateValueFromAttributes(geometryMetadata, "TOP_RIGHT_LONGITUDE");
        final double pixelSizeX = Math.abs(topRightLon - topLeftLon) / productWidth;
        final double topLeftLat = northing;
        final double bottomLeftLat = ProbaVUtils.getGeometryCoordinateValueFromAttributes(geometryMetadata, "BOTTOM_LEFT_LATITUDE");
        final double pixelSizeY = (topLeftLat - bottomLeftLat) / productHeight;

        final H5Group h5RootGroup = (H5Group) ((DefaultMutableTreeNode) inputFileRootNode).getUserObject();
        final List rootMetadata = h5RootGroup.getMetadata();
        final String crsString = ProbaVUtils.getGeometryCrsStringFromAttributes(rootMetadata);
        try {
            final CoordinateReferenceSystem crs = CRS.parseWKT(crsString);
            final CrsGeoCoding geoCoding = new CrsGeoCoding(crs, productWidth, productHeight, easting, northing, pixelSizeX, pixelSizeY);
            product.setGeoCoding(geoCoding);
        } catch (Exception e) {
            BeamLogManager.getSystemLogger().log(Level.WARNING, "Cannot attach geocoding: " + e.getMessage());
        }
    }

    private static void attachSynthesisQualityFlagBand(Product probavProduct, Product flagProduct) {
        FlagCoding probavSmFlagCoding = new FlagCoding(ProbaVConstants.SM_FLAG_BAND_NAME);
        ProbaVUtils.addSynthesisQualityFlags(probavSmFlagCoding);
        ProbaVUtils.addSynthesisQualityMasks(probavProduct);
        probavProduct.getFlagCodingGroup().add(probavSmFlagCoding);
        final Band smFlagBand =
                probavProduct.addBand(ProbaVConstants.SM_FLAG_BAND_NAME, ProductData.TYPE_INT16);
        smFlagBand.setDescription("PROBA-V Synthesis SM Flags");
        smFlagBand.setSampleCoding(probavSmFlagCoding);

        ProbaVSynthesisBitMaskOp bitMaskOp = new ProbaVSynthesisBitMaskOp();
        bitMaskOp.setParameterDefaultValues();
        bitMaskOp.setSourceProduct("sourceProduct", flagProduct);
        Product bitMaskProduct = bitMaskOp.getTargetProduct();
        smFlagBand.setSourceImage(bitMaskProduct.getBand(ProbaVConstants.SM_FLAG_BAND_NAME).getSourceImage());
    }

    private boolean isSynthesisViewAngleGroupNode(String level3GeometryChildNodeName) {
        return level3GeometryChildNodeName.equals("SWIR") || level3GeometryChildNodeName.equals("VNIR");
    }

    private boolean isSynthesisSunAngleDataNode(String level3GeometryChildNodeName) {
        return level3GeometryChildNodeName.equals("SAA") || level3GeometryChildNodeName.equals("SZA");
    }

    private Band createTargetBand(Product product, H5ScalarDS scalarDS, String bandName, int dataType) throws Exception {
        final List<Attribute> metadata = scalarDS.getMetadata();
        final float scaleFactor = ProbaVUtils.getScaleFactorFromAttributes(metadata);
        final float scaleOffset = ProbaVUtils.getScaleOffsetFromAttributes(metadata);
        final Band band = product.addBand(bandName, dataType);
        band.setScalingFactor(scaleFactor);
        band.setScalingOffset(scaleOffset);

        return band;
    }

    private H5ScalarDS getH5ScalarDS(TreeNode level3BandsChildNode) throws HDF5Exception {
        H5ScalarDS scalarDS = (H5ScalarDS) ((DefaultMutableTreeNode) level3BandsChildNode).getUserObject();
        scalarDS.open();
        scalarDS.read();
        return scalarDS;
    }

    private void addSynthesisMetadataElement(List<Attribute> rootMetadata,
                                             final Product product,
                                             String metadataElementName) {
        final MetadataElement metadataElement = new MetadataElement(metadataElementName);

        for (Attribute attribute : rootMetadata) {
            metadataElement.addAttribute(new MetadataAttribute(attribute.getName(),
                                                   ProductData.createInstance(ProbaVUtils.getAttributeValue(attribute)), true));
        }
        product.getMetadataRoot().addElement(metadataElement);
    }

}