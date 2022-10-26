package PML_FixedCells_Tools;

import PML_FixedCells_StardistOrion.StarDist2D;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.RGBStackMerge;
import ij.plugin.ZProjector;
import ij.process.ImageProcessor;
import io.scif.DependencyException;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import javax.swing.ImageIcon;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.plugins.util.ImageProcessorReader;
import mcib3d.geom2.BoundingBox;
import mcib3d.geom2.Object3DComputation;
import mcib3d.geom2.Object3DInt;
import mcib3d.geom2.Objects3DIntPopulation;
import mcib3d.geom2.Objects3DIntPopulationComputation;
import mcib3d.geom2.measurements.MeasureIntensity;
import mcib3d.geom2.measurements.MeasureObject;
import mcib3d.geom2.measurements.MeasureVolume;
import mcib3d.geom2.measurementsPopulation.MeasurePopulationColocalisation;
import mcib3d.image3d.ImageHandler;
import mcib3d.image3d.ImageInt;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import org.apache.commons.io.FilenameUtils;
import org.scijava.util.ArrayUtils;
import mcib3d.spatial.descriptors.F_Function;
import mcib3d.spatial.descriptors.SpatialDescriptor;
import mcib3d.spatial.sampler.SpatialModel;
import mcib3d.spatial.sampler.SpatialRandomHardCore;
import mcib3d.geom.Object3D;
import mcib3d.geom.Objects3DPopulation;

/**
 * @author phm
 */
public class Tools {
    private CLIJ2 clij2 = CLIJ2.getInstance();
    private final ImageIcon icon = new ImageIcon(this.getClass().getResource("/Orion_icon.png"));
    
    public String[] channelNames = {"DAPI", "PML", "None"};
    public Calibration cal = new Calibration();
    public double pixVol = 0;
    
    private Object syncObject = new Object();
    private final double stardistPercentileBottom = 0.2;
    private final double stardistPercentileTop = 99.8;
    private final double stardistProbThreshNuc = 0.5;
    private final double stardistOverlayThreshNuc = 0.25;
    private final double stardistProbThreshDot = 0.1;
    private final double stardistOverlayThreshDot = 0.25;
    private File modelsPath = new File(IJ.getDirectory("imagej")+File.separator+"models");
    public String stardistNucModel = "StandardFluo.zip";
    public String stardistFociModel = "pmls2.zip";
    private String stardistOutput = "Label Image"; 
    
    private double minNucVol = 100;
    private double maxNucVol = 5000;
    private double intensityThresh = 50;
    private double minFociVol = 0.01;
    private double maxFociVol = 20;
    private boolean computeSdi = true;
    
    
    /**
     * Display a message in the ImageJ console and status bar
     */
    public void print(String log) {
        System.out.println(log);
        IJ.showStatus(log);
    }
    
    
    /**
     * Check that needed modules are installed
     */
    public boolean checkInstalledModules() {
        ClassLoader loader = IJ.getClassLoader();
        try {
            loader.loadClass("mcib3d.geom.Object3D");
        } catch (ClassNotFoundException e) {
            IJ.showMessage("Error", "3D ImageJ Suite not installed, please install from update site");
            return false;
        }
        try {
            loader.loadClass("net.haesleinhuepf.clij2.CLIJ2");
        } catch (ClassNotFoundException e) {
            IJ.log("CLIJ not installed, please install from update site");
            return false;
        }
        return true;
    }
    
    
    /**
     * Check that required StarDist models are present in Fiji models folder
     */
    public boolean checkStarDistModels() {
        FilenameFilter filter = (dir, name) -> name.endsWith(".zip");
        File[] modelList = modelsPath.listFiles(filter);
        int index = ArrayUtils.indexOf(modelList, new File(modelsPath+File.separator+stardistNucModel));
        if (index == -1) {
            IJ.showMessage("Error", stardistNucModel + " StarDist model not found, please add it in Fiji models folder");
            return false;
        }
        index = ArrayUtils.indexOf(modelList, new File(modelsPath+File.separator+stardistFociModel));
        if (index == -1) {
            IJ.showMessage("Error", stardistFociModel + " StarDist model not found, please add it in Fiji models folder");
            return false;
        }
        return true;
    }
    
    
    /**
     * Find image type
     */
    public String findImageType(File imagesFolder) {
        String ext = "";
        String[] files = imagesFolder.list();
        for (String name : files) {
            String fileExt = FilenameUtils.getExtension(name);
            switch (fileExt) {
               case "nd" :
                   ext = fileExt;
                   break;
                case "czi" :
                   ext = fileExt;
                   break;
                case "lif"  :
                    ext = fileExt;
                    break;
                case "isc2" :
                    ext = fileExt;
                    break;
                case "tif" :
                    ext = fileExt;
                    break;    
            }
        }
        return(ext);
    }
    
    
    /**
     * Find images in folder
     */
    public ArrayList<String> findImages(String imagesFolder, String imageExt) {
        File inDir = new File(imagesFolder);
        String[] files = inDir.list();
        if (files == null) {
            System.out.println("No image found in "+imagesFolder);
            return null;
        }
        ArrayList<String> images = new ArrayList();
        for (String f : files) {
            // Find images with extension
            String fileExt = FilenameUtils.getExtension(f);
            if (fileExt.equals(imageExt))
                images.add(imagesFolder + File.separator + f);
        }
        Collections.sort(images);
        return(images);
    }
    
    
    /**
     * Find image calibration
     */
    public Calibration findImageCalib(IMetadata meta) {
        cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
        cal.pixelHeight = cal.pixelWidth;
        if (meta.getPixelsPhysicalSizeZ(0) != null)
            cal.pixelDepth = meta.getPixelsPhysicalSizeZ(0).value().doubleValue();
        else
            cal.pixelDepth = 1;
        cal.setUnit("microns");
        System.out.println("XY calibration = " + cal.pixelWidth + ", Z calibration = " + cal.pixelDepth);
        return(cal);
    }
    
    
     /**
     * Find channels name
     * @throws loci.common.services.DependencyException
     * @throws loci.common.services.ServiceException
     * @throws loci.formats.FormatException
     * @throws java.io.IOException
     */
    public String[] findChannels (String imageName, IMetadata meta, ImageProcessorReader reader) throws DependencyException, ServiceException, FormatException, IOException {
        int chs = reader.getSizeC();
        String[] channels = new String[chs];
        String imageExt =  FilenameUtils.getExtension(imageName);
        switch (imageExt) {
            case "nd" :
                for (int n = 0; n < chs; n++) 
                {
                    if (meta.getChannelID(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelName(0, n).toString();
                }
                break;
            case "lif" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null || meta.getChannelName(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelName(0, n).toString();
                break;
            case "czi" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelFluor(0, n).toString();
                break;
            case "ics" :
                for (int n = 0; n < chs; n++) 
                    if (meta.getChannelID(0, n) == null)
                        channels[n] = Integer.toString(n);
                    else 
                        channels[n] = meta.getChannelExcitationWavelength(0, n).value().toString();
                break;    
            default :
                for (int n = 0; n < chs; n++)
                    channels[n] = Integer.toString(n);
        }
        return(channels);         
    }
    
        
    /**
     * Generate dialog box
     */
    public String[] dialog(String[] chs) {      
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.setInsets​(0, 100, 0);
        gd.addImage(icon);
        
        if (chs.length == 3)
            channelNames[2] = "Partner";
        gd.addMessage("Channels", Font.getFont("Monospace"), Color.blue);
        int index = 0;
        for (String chNames : channelNames) {
            gd.addChoice(chNames+" : ", chs, chs[index]);
            index++;
        }
        
        gd.addMessage("Nuclei detection", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Min nucleus volume (µm3): ", minNucVol);
        gd.addNumericField("Max nucleus volume (µm3): ", maxNucVol);
        gd.addNumericField("Intensity threshold (*background): ", intensityThresh); 
        
        gd.addMessage("Foci detection", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Min foci volume (µm3): ", minFociVol);
        gd.addNumericField("Max foci volume (µm3): ", maxFociVol);
        gd.addCheckbox("Compute Spatial Distribution Index", computeSdi);
        
        gd.addMessage("Image calibration", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("XY pixel size (µm): ", cal.pixelWidth);
        gd.addNumericField("Z pixel size (µm): ", cal.pixelDepth);
        gd.showDialog();
        
        String[] chChoices = new String[channelNames.length];
        for (int n = 0; n < chChoices.length; n++) 
            chChoices[n] = gd.getNextChoice();
        if (gd.wasCanceled())
            chChoices = null;        

        minNucVol = gd.getNextNumber();
        maxNucVol = gd.getNextNumber();
        intensityThresh = gd.getNextNumber();
        minFociVol = gd.getNextNumber();
        maxFociVol = gd.getNextNumber();
        computeSdi = gd.getNextBoolean();
        
        cal.pixelWidth = cal.pixelHeight = gd.getNextNumber();
        cal.pixelDepth = gd.getNextNumber();
        pixVol = cal.pixelWidth*cal.pixelHeight*cal.pixelDepth;  
        
        return(chChoices);
    }
     
    
    /**
     * Flush and close an image
     */
    public void flush_close(ImagePlus img) {
        img.flush();
        img.close();
    }
    
    
    /**
     * Median filter using CLIJ2
     */ 
    public ImagePlus median_filter(ImagePlus img, double sizeXY, double sizeZ) {
       ClearCLBuffer imgCL = clij2.push(img);
       ClearCLBuffer imgCLMed = clij2.create(imgCL);
       clij2.median3DBox(imgCL, imgCLMed, sizeXY, sizeXY, sizeZ);
       clij2.release(imgCL);
       ImagePlus imgMed = clij2.pull(imgCLMed);
       clij2.release(imgCLMed);
       return(imgMed);
    }
    
    
    /**
     * Stitch 2D masks into 3D volume using centroids
     */
    /*public ImagePlus stitch3D(ImagePlus img) {
        ImageStack imgOut = new ImageStack();
        imgOut.addSlice(img.getStack().getProcessor(1));
        for (int i=1; i < img.getStack().size(); i++) {
            Objects3DIntPopulation population1 = new Objects3DIntPopulation(ImageHandler.wrap(new Duplicator().run(new ImagePlus("", imgOut), i, i)));
            Objects3DIntPopulation population2 = new Objects3DIntPopulation(ImageHandler.wrap(new Duplicator().run(img, i+1, i+1)));
            for (Object3DInt obj2: population2.getObjects3DInt()) {
                for (Object3DInt obj1: population1.getObjects3DInt()) {
                    if (obj1.contains(new MeasureCentroid​(obj2).getCentroidRoundedAsVoxelInt())) {
                        obj2.setLabel(obj1.getLabel());
                        break;
                    }
                }
            }
            imgOut.addSlice(population2.drawImage().getImagePlus().getProcessor());
        }
        return new ImagePlus("", imgOut);
    }*/
    
    
    /**
     * Do Z projection
     * @param img
     * @param projection parameter
     */
    public ImagePlus doZProjection(ImagePlus img, int param) {
        ZProjector zproject = new ZProjector();
        zproject.setMethod(param);
        zproject.setStartSlice(1);
        zproject.setStopSlice(img.getNSlices());
        zproject.setImage(img);
        zproject.doProjection();
       return(zproject.getProjection());
    }
    
    
    /**
     * Find background image intensity:
     * Z projection over min intensity + read mean intensity
     * @param img
     */
    public double findBackground(ImagePlus img) {
      double[] bg = new double[2];
      ImagePlus imgProj = doZProjection(img, ZProjector.MIN_METHOD);
      ImageProcessor imp = imgProj.getProcessor();
      bg[0] = imp.getStatistics().mean;
      bg[1] = imp.getStatistics().stdDev;
      System.out.println("Background (mean +- std of the min projection) = " + bg[0] + " +- " + bg[1]);
      flush_close(imgProj);
      return(bg[0]+bg[1]);
    }
    
    
    public ImagePlus filterDetectionsByIntensity(ImagePlus imgLabels, ImagePlus imgRaw) {
        double background = findBackground(imgRaw);
        Objects3DIntPopulation popIn = new Objects3DIntPopulation(ImageHandler.wrap(imgLabels.getStack()));
        Objects3DIntPopulation popOut = new Objects3DIntPopulation();
        ImageHandler imhRaw = ImageHandler.wrap(imgRaw);
        for (Object3DInt obj: popIn.getObjects3DInt()) {
            if (new MeasureIntensity(obj, imhRaw).getValueMeasurement(MeasureIntensity.INTENSITY_AVG) > intensityThresh*background) {
                popOut.addObject(obj);
            }
        }
        return(popOut.drawImage().getImagePlus());
    }
    
    
    /**
     * Remove objects present in only one z slice from population 
     */
    public Objects3DIntPopulation zFilterPop(Objects3DIntPopulation pop) {
        Objects3DIntPopulation popZ = new Objects3DIntPopulation();
        for (Object3DInt obj : pop.getObjects3DInt()) {
            int zmin = obj.getBoundingBox().zmin;
            int zmax = obj.getBoundingBox().zmax;
            if (zmax != zmin)
                popZ.addObject(obj);
        }
        return popZ;
    }
    
    
    /**
     * Apply StarDist 2D slice by slice
     * Label detections in 3D
     * @return objects population
     */
   public Objects3DIntPopulation stardistNucleiPop(ImagePlus imgNuc, ArrayList<Nucleus>  nuclei) throws IOException{
       // Resize image to be in a StarDist-friendly scale
       ImagePlus img = null;
       float factor = 0.25f;
       boolean resize = false;
       if (imgNuc.getWidth() > 1024) {
           img = imgNuc.resize((int)(imgNuc.getWidth()*factor), (int)(imgNuc.getHeight()*factor), 1, "none");
           resize = true;
       } else {
           img = new Duplicator().run(imgNuc);
       }
       
       // Remove outliers
       IJ.run(img, "Remove Outliers", "block_radius_x=10 block_radius_y=10 standard_deviations=1 stack");
       
       // StarDist
       File starDistModelFile = new File(modelsPath+File.separator+stardistNucModel);
       StarDist2D star = new StarDist2D(syncObject, starDistModelFile);
       star.loadInput(img);
       star.setParams(stardistPercentileBottom, stardistPercentileTop, stardistProbThreshNuc, stardistOverlayThreshNuc, stardistOutput);
       star.run();
       flush_close(img);
       
       // Label detections in 3D
       ImagePlus imgOut = (resize) ? star.getLabelImagePlus().resize(imgNuc.getWidth(), imgNuc.getHeight(), 1, "none") : star.getLabelImagePlus();       
       ImagePlus imgLabels = star.associateLabels(filterDetectionsByIntensity(imgOut, imgNuc));
       imgLabels.setCalibration(cal); 
       flush_close(imgOut);
       
       Objects3DIntPopulation pop = new Objects3DIntPopulation(ImageHandler.wrap(imgLabels));      
       Objects3DIntPopulation popExcludeBorders = new Objects3DIntPopulationComputation(pop).getExcludeBorders(ImageHandler.wrap(imgNuc), false);
       Objects3DIntPopulation popFilterSize = new Objects3DIntPopulationComputation(popExcludeBorders).getFilterSize(minNucVol/pixVol, maxNucVol/pixVol);
       Objects3DIntPopulation popZFilter = zFilterPop(popFilterSize);
       popZFilter.resetLabels();
       flush_close(imgLabels);
       
       // Instantiate Nuclei
       for (Object3DInt obj: popZFilter.getObjects3DInt()) {
           double objVol = new MeasureVolume(obj).getVolumeUnit();
           nuclei.add(new Nucleus((int)obj.getLabel(), objVol, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0, 0.0));
       }
       
       return(popZFilter);
   }
   
   
    /**
     * Parallel version of find foci in nucleus
     */
    /*public ArrayList<Objects3DIntPopulation> stardistMultiFociInCellsPop(ImagePlus img, Objects3DIntPopulation nucPop, ArrayList<Nucleus> nucleus, 
            String fociType) throws IOException {
        //
        int nNuc = nucPop.getNbObjects();
        ArrayList<Objects3DIntPopulation> foci = new ArrayList<Objects3DIntPopulation>();
        {
            // try parallelize
            final AtomicInteger ai = new AtomicInteger(0);
            int ncpu = (int) Math.ceil(ThreadUtil.getNbCpus()*0.9);
            Thread[] threads = ThreadUtil.createThreadArray(ncpu);
            final int neach = (int) Math.ceil((double)nNuc/(double)ncpu);
            // Look for nuclei
            for (int iThread=0; iThread<threads.length; iThread++) {
                threads[iThread] = new Thread(){
                    public void run(){
                        for (int k=ai.getAndIncrement(); k<ncpu; k=ai.getAndIncrement()) {
                            for (int n = neach*k; ((n<(neach*(k+1))&&(n<nNuc))); n++) {
                                System.out.println("Doing parallel nucleus "+n);
                                Objects3DIntPopulation allFociPop = new Objects3DIntPopulation();
                                float fociIndex = 0;
                                Object3DInt cell = nucPop.getObjectByLabel(n);
                                BoundingBox box = cell.getBoundingBox();
                                int ZStartCell = box.zmin +1;
                                int ZStopCell = box.zmax + 1;
                                Roi roiBox = new Roi(box.xmin, box.ymin, box.xmax-box.xmin + 1 , box.ymax - box.ymin + 1);
                                img.setRoi(roiBox);
                                img.updateAndDraw();
                                // Crop image
                                ImagePlus imgCell = new Duplicator().run(img, ZStartCell, ZStopCell);
                                imgCell.deleteRoi();
                                imgCell.updateAndDraw();
                                ClearCLBuffer imgCL = clij2.push(imgCell);
                                ClearCLBuffer imgCLM = clij2.create(imgCL);
                                imgCLM = median_filter(imgCL, 2, 2);
                                clij2.release(imgCL);
                                ImagePlus imgM = clij2.pull(imgCLM);
                                clij2.release(imgCLM);

                                // Go StarDist
                                File starDistModelFile = new File(modelsPath+File.separator+stardistFociModel);
                                StarDist2D star = new StarDist2D(syncObject, starDistModelFile);
                                star.loadInput(imgM);
                                star.setParams(stardistPercentileBottom, stardistPercentileTop, stardistProbThreshDot, stardistOverlayThreshDot, stardistOutput);
                                star.run();

                                // label in 3D
                                ImagePlus imgLabels = star.associateLabels();
                                imgLabels.setCalibration(cal);
                                ImageInt label3D = ImageInt.wrap(imgLabels);
                                Objects3DIntPopulation fociPop = new Objects3DIntPopulationComputation(new Objects3DIntPopulation(label3D)).
                                        getFilterSize(minFociVol/pixVol, maxFociVol/pixVol);
                                // Remove objects with one Z
                                fociPop = zFilterPop(fociPop);

                                // find foci in cell
                                Object3DInt cellT = new Object3DComputation(cell).getObject3DCopy();
                                cellT.translate(-box.xmin, -box.ymin, -ZStartCell + 1);
                                Objects3DIntPopulation fociColocPop = findColocCell(cellT, fociPop);
                                System.out.println(fociColocPop.getNbObjects()+" foci "+fociType+" found in  nucleus "+cell.getLabel());
                                // write foci parameters in nucleus
                                writeFociParameters(cellT, fociColocPop, imgM, nucleus, fociType);
                                // reset foci in global image
                                int tx = box.xmin;
                                int ty = box.ymin;
                                int tz = ZStartCell;
                                for (Object3DInt foci: fociColocPop.getObjects3DInt()) {
                                    foci.translate(tx, ty, tz-1);
                                    fociIndex++;
                                    foci.setLabel(fociIndex);
                                    foci.setType((int)cell.getLabel());
                                    allFociPop.addObject(foci);
                                }
                                flush_close(imgCell);
                                flush_close(imgLabels);
                                flush_close(imgM);
                                foci.add(fociPop);
                            }
                        }
                    }
                };
            }
            ThreadUtil.startAndJoin(threads);
            threads = null;
        }
        return(foci);
    }*/
   
   
   /**
     * Translate objects and their respective bouding box in a population 
     */
   /*public void translatePop(Objects3DIntPopulation pop, int x, int y, int z) {
        pop.translateObjects(x, y, z);
        for (Object3DInt foci: pop.getObjects3DInt()) {
            BoundingBox fociBox = foci.getBoundingBox();
            fociBox.setBounding(fociBox.xmin+x, fociBox.xmax+x, fociBox.ymin+y, fociBox.ymax+y, fociBox.zmin+z, fociBox.zmax+z);
        }
    }*/
   
   
    /**
     * Find dots population colocalizing with a cell objet 
     */
    public Objects3DIntPopulation findColocCell(Object3DInt cellObj, Objects3DIntPopulation dotsPop) {
        Objects3DIntPopulation cellPop = new Objects3DIntPopulation();
        cellPop.addObject(cellObj);
        Objects3DIntPopulation colocPop = new Objects3DIntPopulation();
        if (dotsPop.getNbObjects() > 0) {
            MeasurePopulationColocalisation coloc = new MeasurePopulationColocalisation(cellPop, dotsPop);
            for (Object3DInt dot: dotsPop.getObjects3DInt()) {
                    double colocVal = coloc.getValueObjectsPair(cellObj, dot);
                    if (colocVal > 0.75*dot.size()) {
                        colocPop.addObject(dot);
                    }
            }
        }
        return(colocPop);
    }
    
    
    /**
     * Read diffuse intensity in nucleus:
     * - fill foci voxels with zero in foci channel
     * - compute sum intensity outside foci
     */
    public double fociDiffuseInt(Object3DInt nucObj, Objects3DIntPopulation fociPop, ImagePlus imgFoci) {
        float dilation = 1.5f;
        ImageHandler imhFoci = ImageHandler.wrap(imgFoci.duplicate());
        for (Object3DInt obj: fociPop.getObjects3DInt()) {
            Object3DInt dilatedObj = new Object3DComputation(obj).getObjectDilated(dilation, dilation, dilation);
            dilatedObj.drawObject(imhFoci, 0);
        }
        double fociDiffuseInt = new MeasureObject(nucObj).measureIntensity(MeasureIntensity.INTENSITY_SUM, imhFoci); 
        imhFoci.closeImagePlus();
        return(fociDiffuseInt);
    }
    
    
    /**
     * Compute F-function-related Spatial Distribution Index of foci population in a nucleus
     * https://journals.plos.org/ploscompbiol/article?id=10.1371/journal.pcbi.1000853
     */ 
    public Double computeSdiF(Objects3DIntPopulation fociInt, Object3DInt nucInt, ImagePlus img) {
        // Convert Object3DInt & Objects3DIntPopulation objects into Object3D & Objects3DPopulation objects
        ImageHandler imhNuc = ImageHandler.wrap(img).createSameDimensions();
        nucInt.drawObject(imhNuc, 1);
        Object3D nuc = new Objects3DPopulation(imhNuc).getObject(0);
        ImageHandler imhFoci = ImageHandler.wrap(img).createSameDimensions();
        fociInt.drawInImage(imhFoci);
        Objects3DPopulation foci = new Objects3DPopulation(imhFoci);
        // Define spatial descriptor and model
        SpatialDescriptor spatialDesc = new F_Function(2500, nuc); // nb of points used to compute the F-function        
        SpatialModel spatialModel = new SpatialRandomHardCore(foci.getNbObjects(), 0.8/cal.pixelWidth, nuc); // average diameter of a spot in pixels
        SpatialStatistics spatialStatistics = new SpatialStatistics(spatialDesc, spatialModel, 100, foci); // nb of samples (randomized organizations simulated to compare with the spatial organization of the spots)
        spatialStatistics.setEnvelope(0.05); // 2.5-97.5% envelope error
        spatialStatistics.setVerbose(false);
        return(spatialStatistics.getSdi());
    }
    
    
    /**
     * Compute foci parameters and save them in corresponding Nucleus
     */
    public void writeFociParameters(Object3DInt nuc, Objects3DIntPopulation fociColocPop, ImagePlus img, ArrayList<Nucleus> nuclei, String fociType) {
        int index = (int)(nuc.getLabel() - 1);
        int fociNb = fociColocPop.getNbObjects();
        double fociVol = 0;
        double fociInt = 0;
        ImageHandler imh = ImageHandler.wrap(img.duplicate());
        for (Object3DInt obj: fociColocPop.getObjects3DInt()) {
            fociVol += new MeasureVolume(obj).getVolumeUnit();
            fociInt += new MeasureIntensity(obj, imh).getValueMeasurement(MeasureIntensity.INTENSITY_SUM);
        }
        double fociIntDif = fociDiffuseInt(nuc, fociColocPop, img);
        Double fociSdiF = null;
        if(computeSdi) 
            fociSdiF = computeSdiF(fociColocPop, nuc, img);
        
        switch (fociType) {
            case "PML" :
                nuclei.get(index).setNucPmlFoci(fociNb);
                nuclei.get(index).setNucPmlVol(fociVol);
                nuclei.get(index).setNucPmlFociInt(fociInt);
                nuclei.get(index).setNucPmlInt(fociIntDif);
                nuclei.get(index).setNucPmlSdiF(fociSdiF);
                break;
            case "Partner" :
                nuclei.get(index).setNucPartnerFoci(fociNb);
                nuclei.get(index).setNucPartnerVol(fociVol);
                nuclei.get(index).setNucPartnerFociInt(fociInt);
                nuclei.get(index).setNucPartnerInt(fociIntDif);
                nuclei.get(index).setNucPartnerSdiF(fociSdiF);
                break;
        }
    }
    
    
    /** 
    * For each nucleus find foci
    * return foci pop cell population
    */
    public Objects3DIntPopulation stardistFociInCellsPop(ImagePlus img, Objects3DIntPopulation nucPop, ArrayList<Nucleus> nuclei, String fociType) throws IOException{
        float fociIndex = 1;
        Objects3DIntPopulation allFociPop = new Objects3DIntPopulation();
        for (Object3DInt nuc: nucPop.getObjects3DInt()) {
            // Crop image around nucleus
            BoundingBox box = nuc.getBoundingBox();
            Roi roiBox = new Roi(box.xmin, box.ymin, box.xmax-box.xmin, box.ymax-box.ymin);
            img.setRoi(roiBox);
            img.updateAndDraw();
            ImagePlus imgNuc = new Duplicator().run(img, box.zmin+1, box.zmax+1);
            imgNuc.deleteRoi();
            imgNuc.updateAndDraw();
            
            // Downscaling and median filter
            ImagePlus imgS  = imgNuc.resize((int)(0.5*imgNuc.getWidth()), (int)(0.5*imgNuc.getHeight()), 1, "none");
            ImagePlus imgM = median_filter(imgS, 1, 1);
            flush_close(imgS);

            // StarDist
            File starDistModelFile = new File(modelsPath+File.separator+stardistFociModel);
            StarDist2D star = new StarDist2D(syncObject, starDistModelFile);
            star.loadInput(imgM);
            star.setParams(stardistPercentileBottom, stardistPercentileTop, stardistProbThreshDot, stardistOverlayThreshDot, stardistOutput);
            star.run();
            flush_close(imgM);

            // Label foci in 3D
            ImagePlus imgOut = star.getLabelImagePlus();
            ImagePlus imgLabels = star.associateLabels(imgOut).resize(imgNuc.getWidth(), imgNuc.getHeight(), 1, "none");
            flush_close(imgNuc);
            flush_close(imgOut);
            imgLabels.setCalibration(cal);
            ImageInt label3D = ImageInt.wrap(imgLabels);
            Objects3DIntPopulation fociPop = new Objects3DIntPopulationComputation(new Objects3DIntPopulation(label3D)).getFilterSize(minFociVol/pixVol, maxFociVol/pixVol);
            fociPop = zFilterPop(fociPop);
            fociPop.resetLabels();
            flush_close(imgLabels);
            
            // Find foci in nucleus
            fociPop.translateObjects(box.xmin, box.ymin, box.zmin);
            Objects3DIntPopulation fociColocPop = findColocCell(nuc, fociPop);
            System.out.println(fociColocPop.getNbObjects() + " " + fociType + " foci found in nucleus " + nuc.getLabel());
            
            for (Object3DInt foci: fociColocPop.getObjects3DInt()) {
                foci.setLabel(fociIndex);
                fociIndex++;
                foci.setType((int)nuc.getLabel());
                allFociPop.addObject(foci);
            }
            
            writeFociParameters(nuc, fociColocPop, img, nuclei, fociType);
        }
        return(allFociPop);
    }
   
    
    /**
     * Find partner/pml associated to one nucleus
     */
    public Objects3DIntPopulation findFociNuc(int nucN, Objects3DIntPopulation fociPop) {
        Objects3DIntPopulation pop = new Objects3DIntPopulation();
        for (Object3DInt partObj : fociPop.getObjects3DInt()) {
                if (partObj.getType() == nucN)
                    pop.addObject(partObj);
        }
        return(pop);
    }
    
    
    /**
     * Find partner coloc with pml
     */
    public void findColocPartnerPml(int nucNb, Objects3DIntPopulation partnerFociPop, Objects3DIntPopulation pmlFociPop, ArrayList<Nucleus> nuclei) {
        for (int n = 1; n <= nucNb; n++) {
            int partnerNb = 0;
            double partnerColocVol = 0;
            double overlapColocVol = 0;
            
            // Get Partner and PML foci in nucleus
            Objects3DIntPopulation partnerNuc = findFociNuc(n, partnerFociPop);
            Objects3DIntPopulation pmlNuc = findFociNuc(n, pmlFociPop);
            
            // Colocalization
            MeasurePopulationColocalisation coloc = new MeasurePopulationColocalisation(partnerNuc, pmlNuc);
            for (Object3DInt partnerObj: partnerFociPop.getObjects3DInt()) {
                for (Object3DInt pmlObj: pmlFociPop.getObjects3DInt()) {
                    double colocVal = coloc.getValueObjectsPair(partnerObj, pmlObj);
                    if (colocVal > 0.25*partnerObj.size()) {
                        partnerNb++;
                        partnerColocVol += new MeasureVolume(partnerObj).getVolumeUnit();
                        overlapColocVol += colocVal;
                    }
                }
            }
            System.out.println(partnerNb + " Partner foci colocalized with PML foci in nucleus " + n);
            nuclei.get(n-1).setNucPartnerPmlColocFoci(partnerNb);
            nuclei.get(n-1).setNucPartnerPmlColocVolFoci(partnerColocVol);
            nuclei.get(n-1).setNucPartnerPmlColocVolOverlap(overlapColocVol*pixVol);
        }
    }
    
    
    /**
     * Label object
     * @param popObj
     * @param img 
     * @param fontSize 
     */
    public void labelObject(Object3DInt obj, ImagePlus img, int fontSize) {
        if (IJ.isMacOSX())
            fontSize *= 3;
        
        BoundingBox bb = obj.getBoundingBox();
        int z = bb.zmin + 1;
        int x = bb.xmin;
        int y = bb.ymin;
        img.setSlice(z);
        ImageProcessor ip = img.getProcessor();
        ip.setFont(new Font("SansSerif", Font.PLAIN, fontSize));
        ip.setColor(255);
        ip.drawString(Integer.toString((int)obj.getLabel()), x, y);
        img.updateAndDraw();
    }
        
     
     /**
     * Save foci Population in image
     * @param pop1 nucleus blue channel
     * @param pop2 pml foci in green channel
     * @param pop3 partner foci red channel
     * @param img 
     * @param outDir 
     */
    public void saveImgObjects(Objects3DIntPopulation pop1, Objects3DIntPopulation pop2, Objects3DIntPopulation pop3, ImagePlus img, String imageName, String outDir) {
        // Draw DAPI nuclei in blue
        ImageHandler imgObj1 = ImageHandler.wrap(img).createSameDimensions();
        ImageHandler imgObj2 = imgObj1.createSameDimensions();
        pop1.drawInImage(imgObj1);
        if (pop1.getNbObjects() > 0)
            for (Object3DInt obj: pop1.getObjects3DInt())
                labelObject(obj, imgObj2.getImagePlus(), 40);
        
        // Draw PML foci in green
        ImageHandler imgObj3 = imgObj1.createSameDimensions();
        if (pop2.getNbObjects() > 0)
            for (Object3DInt obj: pop2.getObjects3DInt())
                obj.drawObject(imgObj3, obj.getType());
        
        // Draw Partner foci in red
        ImageHandler imgObj4 = imgObj1.createSameDimensions();
        if (pop3.getNbObjects() > 0)
            for (Object3DInt obj: pop3.getObjects3DInt())
                obj.drawObject(imgObj4, obj.getType());
        
        // Save image
        ImagePlus[] imgColors = {imgObj4.getImagePlus(), imgObj3.getImagePlus(), imgObj1.getImagePlus(), imgObj2.getImagePlus()};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(img.getCalibration());
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDir + imageName + ".tif"); 
        imgObj1.closeImagePlus();
        imgObj2.closeImagePlus();
        imgObj3.closeImagePlus();
        flush_close(imgObjects);
    }
}
