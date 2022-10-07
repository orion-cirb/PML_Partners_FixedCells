package PML_FixedCells_Tools;


import StardistOrion.StarDist2D;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.Roi;
import ij.gui.WaitForUserDialog;
import ij.io.FileSaver;
import ij.measure.Calibration;
import ij.plugin.Duplicator;
import ij.plugin.RGBStackMerge;
import ij.util.ThreadUtil;
import io.scif.DependencyException;
import java.awt.Color;
import java.awt.Font;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
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
import mcib3d.geom2.measurements.Measure2Colocalisation;
import mcib3d.geom2.measurements.MeasureIntensity;
import mcib3d.geom2.measurements.MeasureObject;
import mcib3d.geom2.measurements.MeasureVolume;
import mcib3d.image3d.ImageHandler;
import mcib3d.image3d.ImageInt;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import org.apache.commons.io.FilenameUtils;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author phm
 */
public class Tools {

    public boolean canceled = false;
    // min max volume in microns^3 for nucleus
    private double minNuc = 100;
    private double maxNuc = 5000;

    // min max volume in microns^3 for dots
    private double minFoci = 0.01;
    private double maxFoci = 10;
    
    private Object syncObject = new Object();
    private final double stardistPercentileBottom = 0.2;
    private final double stardistPercentileTop = 99.8;
    private final double stardistProbThreshNuc = 0.5;
    private final double stardistOverlayThreshNuc = 0.25;
    private final double stardistProbThreshDot = 0.05;
    private final double stardistOverlayThreshDot = 0.25;
    private File modelsPath = new File(IJ.getDirectory("imagej")+File.separator+"models");
    private String stardistOutput = "Label Image"; 
    public String stardistNucModel = "";
    public String stardistFociModel = "";
    public double pixVol= 0;
    
    public String[] channelNames = {"DAPI", "PML", "None"};
    public Calibration cal = new Calibration();
        
    private final ImageIcon icon = new ImageIcon(this.getClass().getResource("/Orion_icon.png"));

    private CLIJ2 clij2 = CLIJ2.getInstance();
    
    
     /**
     * check  installed modules
     * @return 
     */
    public boolean checkInstalledModules() {
        // check install
        ClassLoader loader = IJ.getClassLoader();
        try {
            loader.loadClass("net.haesleinhuepf.clij2.CLIJ2");
        } catch (ClassNotFoundException e) {
            IJ.log("CLIJ not installed, please install from update site");
            return false;
        }
        try {
            loader.loadClass("mcib3d.geom.Object3D");
        } catch (ClassNotFoundException e) {
            IJ.log("3D ImageJ Suite not installed, please install from update site");
            return false;
        }
        return true;
    }
    
     /*
    Find starDist models in Fiji models folder
    */
    public String[] findStardistModels() {
        FilenameFilter filter = (dir, name) -> name.endsWith(".zip");
        File[] modelList = modelsPath.listFiles(filter);
        String[] models = new String[modelList.length];
        for (int i = 0; i < modelList.length; i++) {
            models[i] = modelList[i].getName();
        }
        Arrays.sort(models);
        return(models);
    }   
    
    /* Median filter 
     * Using CLIJ2
     * @param ClearCLBuffer
     * @param sizeXY
     * @param sizeZ
     */ 
    public ClearCLBuffer median_filter(ClearCLBuffer  imgCL, double sizeXY, double sizeZ) {
        ClearCLBuffer imgCLMed = clij2.create(imgCL);
        clij2.mean3DBox(imgCL, imgCLMed, sizeXY, sizeXY, sizeZ);
        clij2.release(imgCL);
        return(imgCLMed);
    }
    
    
    /**
     * Remove object with one Z
     */
    public void zFilterPop (Objects3DIntPopulation pop) {
        Objects3DIntPopulation popZ = new Objects3DIntPopulation();
        for (Object3DInt obj : pop.getObjects3DInt()) {
            int zmin = obj.getBoundingBox().zmin;
            int zmax = obj.getBoundingBox().zmax;
            if (zmax == zmin)
                popZ.removeObject(obj);
        }
        pop.resetLabels();
    }
    
    
    public String[] dialog(String[] chs) {
        if (chs.length == 3)
            channelNames[2] = "Partner";
        String[] models = findStardistModels();
        GenericDialogPlus gd = new GenericDialogPlus("Parameters");
        gd.setInsets​(0, 100, 0);
        gd.addImage(icon);
        gd.addMessage("Channels", Font.getFont("Monospace"), Color.blue);
        int index = 0;
        for (String chNames : channelNames) {
            gd.addChoice(chNames+" : ", chs, chs[index]);
            index++;
        }
        if (models.length == 0) {
            gd.addMessage("No StarDist model found in Fiji !!", Font.getFont("Monospace"), Color.red);
            gd.addFileField("StarDist cell model :", stardistNucModel);
            gd.addFileField("StarDist dots model :", stardistFociModel);
        }
        gd.addMessage("Nucleus detection", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Min nucleus volume : ", minNuc);
        gd.addNumericField("Max nucleus volume : ", maxNuc);
        gd.addMessage("Foci size filter", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("Min Foci volume : ", minFoci);
        gd.addNumericField("Max Foci volume : ", maxFoci);
        gd.addMessage("Image calibration", Font.getFont("Monospace"), Color.blue);
        gd.addNumericField("XY Pixel size : ", cal.pixelWidth);
        gd.addNumericField("Z Pixel size  : ", cal.pixelDepth);
        gd.showDialog();
        if (gd.wasCanceled())
            canceled = true;
        String[] chChoices = new String[channelNames.length];
        for (int n = 0; n < chChoices.length; n++) 
            chChoices[n] = gd.getNextChoice();
        if (models.length == 0) {
            stardistNucModel = gd.getNextString();
            stardistFociModel = gd.getNextString();
        }
        else {
            int indexModel = Arrays.asList(models).indexOf("pmls2.zip");
            stardistFociModel = models[indexModel];
            indexModel = Arrays.asList(models).indexOf("StandardFluo.zip");
            stardistNucModel = models[indexModel];
        }
        if (stardistNucModel.isEmpty() || stardistFociModel.isEmpty()) {
            IJ.error("No model specify !!");
            return(null);
        }
        minNuc = gd.getNextNumber();
        maxNuc = gd.getNextNumber();
        minFoci = gd.getNextNumber();
        maxFoci = gd.getNextNumber();
        cal.pixelWidth = cal.pixelHeight = gd.getNextNumber();
        cal.pixelDepth = gd.getNextNumber();
        pixVol = cal.pixelWidth*cal.pixelHeight*cal.pixelDepth;        
        return(chChoices);
    }
     
    // Flush and close images
    public void flush_close(ImagePlus img) {
        img.flush();
        img.close();
    }

    
/**
     * Find images in folder
     * @param imagesFolder
     * @param imageExt
     * @return 
     */
    public ArrayList<String> findImages(String imagesFolder, String imageExt) {
        File inDir = new File(imagesFolder);
        String[] files = inDir.list();
        if (files == null) {
            System.out.println("No Image found in "+imagesFolder);
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
     * Find image calibration
     * @param meta
     * @return 
     */
    public Calibration findImageCalib(IMetadata meta) {
        // read image calibration
        cal.pixelWidth = meta.getPixelsPhysicalSizeX(0).value().doubleValue();
        cal.pixelHeight = cal.pixelWidth;
        if (meta.getPixelsPhysicalSizeZ(0) != null)
            cal.pixelDepth = meta.getPixelsPhysicalSizeZ(0).value().doubleValue();
        else
            cal.pixelDepth = 1;
        cal.setUnit("microns");
        return(cal);
    }
    
    
     /**
     * Find channels name
     * @param imageName
     * @return 
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
    
     
    /** Look for all nuclei
    Do z slice by slice stardist 
    * return nuclei population
    */
   public Objects3DIntPopulation stardistNucleiPop(ImagePlus imgNuc, ArrayList<Nucleus>  nucleus) throws IOException{
       ImagePlus img = null;
       // resize to be in a stardist-friendly scale
       int width = imgNuc.getWidth();
       int height = imgNuc.getHeight();
       float factor = 0.25f;
       boolean resized = false;
       if (imgNuc.getWidth() > 1024) {
           img = imgNuc.resize((int)(width*factor), (int)(height*factor), 1, "none");
           resized = true;
       }
       else
           img = new Duplicator().run(imgNuc);
       
       IJ.run(img, "Remove Outliers", "block_radius_x=10 block_radius_y=10 standard_deviations=1 stack");
       ClearCLBuffer imgCL = clij2.push(img);
       ClearCLBuffer imgCLM = clij2.create(imgCL);
       imgCLM = median_filter(imgCL, 2, 2);
       clij2.release(imgCL);
       ImagePlus imgM = clij2.pull(imgCLM);
       clij2.release(imgCLM);
       flush_close(img);

       // Go StarDist
       File starDistModelFile = new File(modelsPath+File.separator+stardistNucModel);
       StarDist2D star = new StarDist2D(syncObject, starDistModelFile);
       star.loadInput(imgM);
       star.setParams(stardistPercentileBottom, stardistPercentileTop, stardistProbThreshNuc, stardistOverlayThreshNuc, stardistOutput);
       star.run();
       flush_close(imgM);
       // label in 3D
       ImagePlus nuclei = (resized) ? star.associateLabels().resize(width, height, 1, "none") : star.associateLabels();
       nuclei.setCalibration(cal);
       Objects3DIntPopulation pop = new Objects3DIntPopulation(ImageHandler.wrap(nuclei));
       Objects3DIntPopulation popExcludeBorders = new Objects3DIntPopulationComputation(pop).getExcludeBorders(ImageHandler.wrap(imgNuc), false);
       Objects3DIntPopulation popFilter = new Objects3DIntPopulationComputation(popExcludeBorders).getFilterSize(minNuc/pixVol, maxNuc/pixVol);
       popFilter.resetLabels();
       // write parameters in nucleus
       for (Object3DInt obj : popFilter.getObjects3DInt()) {
           double objVol = new MeasureVolume(obj).getVolumeUnit();
           nucleus.add(new Nucleus((int)obj.getLabel(), objVol, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
       }
       flush_close(nuclei);
       return(popFilter);
   }
   
   
    /**
     * Find objects population coloc in objet 
     */
    public Objects3DIntPopulation findColocCell(Object3DInt cellObj, Objects3DIntPopulation pop1) {
        Objects3DIntPopulation objs = new Objects3DIntPopulation();
        for (Object3DInt obj1 : pop1.getObjects3DInt()) {
            Measure2Colocalisation coloc = new Measure2Colocalisation(obj1, cellObj);
            if (coloc.getValue(Measure2Colocalisation.COLOC_VOLUME) > 0.5*obj1.size())
                objs.addObject(obj1);
        }
        objs.setVoxelSizeXY(cal.pixelWidth);
        objs.setVoxelSizeZ(cal.pixelDepth);
        return(objs);
    }
    
   
    /**
     * Parallel version of find foci in nucleus
     */
    public ArrayList<Objects3DIntPopulation> stardistMultiFociInCellsPop(ImagePlus img, Objects3DIntPopulation nucPop, ArrayList<Nucleus> nucleus, 
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
                                        getFilterSize(minFoci/pixVol, maxFoci/pixVol);
                                // Remove objects with one Z
                                zFilterPop(fociPop);

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
    }

    
    /** 
    * For each nucleus find foci
    * return foci pop cell population
    */
    public Objects3DIntPopulation stardistFociInCellsPop(ImagePlus img, Objects3DIntPopulation nucPop, ArrayList<Nucleus> nucleus, String fociType) throws IOException{
        Objects3DIntPopulation allFociPop = new Objects3DIntPopulation();
        float fociIndex = 0;
        for (Object3DInt cell: nucPop.getObjects3DInt()) {
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
                    getFilterSize(minFoci/pixVol, maxFoci/pixVol);
            // Remove objects with one Z
            zFilterPop(fociPop);
            
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
        }
        return(allFociPop);
    }
     
    /**
     * compute foci parameters and apply to nucleus
     * 
    */
    public void writeFociParameters(Object3DInt nuc, Objects3DIntPopulation fociColocPop, ImagePlus imgM, ArrayList<Nucleus> nucleus, String fociType) {
        double fociIntDif = fociDiffus(fociColocPop, nuc, imgM);
        double fociVol = 0;
        double fociInt = 0;
        int foci = fociColocPop.getNbObjects();
        for (Object3DInt obj : fociColocPop.getObjects3DInt()) {
            fociVol += new MeasureVolume(obj).getVolumeUnit();
            fociInt += new MeasureIntensity(obj, ImageHandler.wrap(imgM)).getValueMeasurement(MeasureIntensity.INTENSITY_SUM);
        }
        int index = (int)nuc.getLabel() -1;
        switch (fociType) {
            case "plm" :
                nucleus.get(index).setNucPmlFoci(foci);
                nucleus.get(index).setNucPmlVol(fociVol);
                nucleus.get(index).setNucPmlFociInt(fociInt);
                nucleus.get(index).setNucPmlInt(fociIntDif);
                break;
            case "partner" :
                nucleus.get(index).setNucPartnerFoci(foci);
                nucleus.get(index).setNucPartnerVol(fociVol);
                nucleus.get(index).setNucPartnerFociInt(fociInt);
                nucleus.get(index).setNucPartnerInt(fociIntDif);
                break;
        }
    }
    
    
    /**
     * Find partner coloc with pml
     */
    public void findColocPartnerPlm(int nucN, Objects3DIntPopulation pmlFociPop, Objects3DIntPopulation partnerFociPop, ArrayList<Nucleus> nucleus) {
        for (int i = 1; i <= nucN; i++) {
            int partnerN = 0;
            double partnerColoVol = 0;
            for (Object3DInt partObj : partnerFociPop.getObjects3DInt()) {
                if (partObj.getType() == i) {
                    for (Object3DInt pmlObj : pmlFociPop.getObjects3DInt()) {
                        if (pmlObj.getType() == i) {
                            Measure2Colocalisation coloc = new Measure2Colocalisation(partObj, pmlObj);
                            double colocMes = coloc.getValue(Measure2Colocalisation.COLOC_VOLUME);
                            System.out.println(colocMes);
                            if (colocMes > 0.25*partObj.size()) {
                                partnerN++;
                                partnerColoVol += new MeasureVolume(partObj).getVolumeUnit();
                            }
                        }
                    }
                }
            }
            nucleus.get(i-1).setNucPartnerPmlColocFoci(partnerN);
            nucleus.get(i-1).setNucPartnerPmlColocVolFoci(partnerColoVol);
        }
    }
    
    
    /**
     * Read diffus intensity in nucleus
     * fill foci voxel with zero in foci channel
     * compute integrated intensity outside foci
     * 
     * @param fociPop
     * @param nucObj
     * @param imgFoci
     */
    public double fociDiffus (Objects3DIntPopulation fociPop, Object3DInt nucObj, ImagePlus imgFoci) {
        ImageHandler imhDotsDiffuse = ImageHandler.wrap(imgFoci.duplicate());
        double fociIntDiffuse ;
        float dilate = 1.5f;
        for (Object3DInt fociObj : fociPop.getObjects3DInt()) {
            // dilate 
            Object3DInt fociDilatedObj = new Object3DComputation(fociObj).getObjectDilated(dilate, dilate, dilate);
            fociDilatedObj.drawObject(imhDotsDiffuse, 0);
        }
        fociIntDiffuse = new MeasureObject(nucObj).measureIntensity(MeasureIntensity.INTENSITY_SUM,imhDotsDiffuse); 
        imhDotsDiffuse.closeImagePlus();
        return(fociIntDiffuse);
    }
    
     
     /**
     * Save foci Population in image
     * @param pop1 nucleus blue channel
     * @param pop2 pml foci in green channel
     * @param pop3 partner foci red channel
     * @param img 
     * @param outDir 
     */
    public void saveImgObjects(Objects3DIntPopulation pop1, Objects3DIntPopulation pop2, Objects3DIntPopulation pop3, String imageName, ImagePlus img, String outDir) {
        //create image objects population
        
        // Nucleus blue
        ImageHandler imgObj1 = ImageHandler.wrap(img).createSameDimensions();
        pop1.drawInImage(imgObj1);
        
        // plm green
        ImageHandler imgObj2 = imgObj1.createSameDimensions();
        if (pop2.getNbObjects() > 0)
            for (Object3DInt obj : pop2.getObjects3DInt())
                obj.drawObject(imgObj2, 255);
        
        ImageHandler imgObj3 = imgObj1.createSameDimensions();
        if (pop3.getNbObjects() > 0)
            for (Object3DInt obj : pop3.getObjects3DInt())
                obj.drawObject(imgObj3, 255);      
        
   
        // save image for objects population
        ImagePlus[] imgColors = {imgObj3.getImagePlus(), imgObj2.getImagePlus(), imgObj1.getImagePlus()};
        ImagePlus imgObjects = new RGBStackMerge().mergeHyperstacks(imgColors, false);
        imgObjects.setCalibration(img.getCalibration());
        FileSaver ImgObjectsFile = new FileSaver(imgObjects);
        ImgObjectsFile.saveAsTiff(outDir + imageName + "_Objects.tif"); 
        imgObj1.closeImagePlus();
        imgObj2.closeImagePlus();
        imgObj3.closeImagePlus();
        flush_close(imgObjects);
    }
    
    
}
