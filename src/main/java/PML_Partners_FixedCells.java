/*
 * Find Nucleus and count PML inside 
 * if Third channel detect dots (partners) anc compute coloc
 * Author Philippe Mailly
 */

import PML_FixedCells_Tools.Nucleus;
import PML_FixedCells_Tools.Tools;
import ij.*;
import ij.plugin.PlugIn;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FormatException;
import loci.formats.meta.IMetadata;
import loci.formats.services.OMEXMLService;
import loci.plugins.BF;
import loci.plugins.util.ImageProcessorReader;
import loci.plugins.in.ImporterOptions;
import mcib3d.geom2.Objects3DIntPopulation;
import org.apache.commons.io.FilenameUtils;
import org.scijava.util.ArrayUtils;



public class PML_Partners_FixedCells implements PlugIn {
    
    Tools tools = new Tools();
    
    private String imageDir = "";
    public String outDirResults = "";
    private boolean canceled = false;
   
    private BufferedWriter outPutResults;
    

    /**
     * 
     * @param arg
     */
    @Override
    public void run(String arg) {
        try {
            imageDir = IJ.getDirectory("Choose directory containing image files...");
            if (imageDir == null) {
                return;
            }   
            // Find images with file_ext extension
            String file_ext = tools.findImageType(new File(imageDir));
            ArrayList<String> imageFiles = tools.findImages(imageDir, file_ext);
            if (imageFiles == null) {
                IJ.showMessage("Error", "No images found with "+file_ext+" extension");
                return;
            }
            
            
            // create OME-XML metadata store of the latest schema version
            ServiceFactory factory;
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            ImageProcessorReader reader = new ImageProcessorReader();
            reader.setMetadataStore(meta);
            // Find channel names , calibration
            reader.setId(imageFiles.get(0));
            tools.cal = tools.findImageCalib(meta);
            String[] chsName = tools.findChannels(imageFiles.get(0), meta, reader);
            
            
            // Channels dialog
            
            String[] channels = tools.dialog(chsName);
            if ( channels == null || tools.canceled) {
                IJ.showStatus("Plugin cancelled");
                return;
            }
            
            // create output folder
            outDirResults = imageDir + File.separator+ "Results"+ File.separator;
            File outDir = new File(outDirResults);
            if (!Files.exists(Paths.get(outDirResults))) {
                outDir.mkdir();
            }
            
            // Write headers results for results file
            FileWriter fileResults = null;
            String resultsName = "results.xls";
            try {
                fileResults = new FileWriter(outDirResults + resultsName, false);
            } catch (IOException ex) {
                Logger.getLogger(PML_Partners_FixedCells.class.getName()).log(Level.SEVERE, null, ex);
            }
            outPutResults = new BufferedWriter(fileResults);
            try {
                outPutResults.write("ImageName\t#Nucleus\tNucleus Volume\tPML foci number\tPML foci volume\tPML foci sum Intensity\tPartner foci number"
                        + "\tPartner foci volume\tPartner foci sum Intensity\tPml diffuse Intensity\tPartner diffuse intensity\tPartner coloc with pml number"
                        + "\tPartner coloc pml volume\n");
                outPutResults.flush();
            } catch (IOException ex) {
                Logger.getLogger(PML_Partners_FixedCells.class.getName()).log(Level.SEVERE, null, ex);
            }
            
            ArrayList<Nucleus>  nucleus = new ArrayList();
            for (String f : imageFiles) {
                reader.setId(f);
                String rootName = FilenameUtils.getBaseName(f);
                ImporterOptions options = new ImporterOptions();
                options.setId(f);
                options.setSplitChannels(true);
                options.setQuiet(true);
                options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
               
                
                // open DAPI Channel
                
                System.out.println("--- Opening nuclei channel  ...");
                int indexCh = ArrayUtils.indexOf(chsName,channels[0]);
                ImagePlus imgDAPI = BF.openImagePlus(options)[indexCh];
                // Find nucleus with stardist
                Objects3DIntPopulation nucPop = tools.stardistNucleiPop(imgDAPI, nucleus);
                int totalDapi = nucPop.getNbObjects();
                System.out.println(totalDapi +" nucleus found");

                // Open Original PML channel to read foci intensity
                System.out.println("--- Opening PML channel  ...");
                indexCh = ArrayUtils.indexOf(chsName,channels[1]);
                ImagePlus imgPML = BF.openImagePlus(options)[indexCh];
                Objects3DIntPopulation pmlFociPop = tools.stardistFociInCellsPop(imgPML, nucPop, nucleus, "plm");
                tools.flush_close(imgPML); 
                
                
                // if partners channel
                // Open Original partners channel to read foci intensity
                Objects3DIntPopulation partnerFociPop = new Objects3DIntPopulation();
                if (chsName.length > 2) {
                    System.out.println("--- Opening partner channel  ...");
                    indexCh = ArrayUtils.indexOf(chsName,channels[2]);
                    ImagePlus imgPartner = BF.openImagePlus(options)[indexCh];
                    partnerFociPop = tools.stardistFociInCellsPop(imgPartner, nucPop, nucleus, "partner");
                    tools.flush_close(imgPartner); 
                    // Find coloc partner/pml
                    tools.findColocPartnerPlm(nucPop.getNbObjects(), pmlFociPop, partnerFociPop, nucleus);
                }
                
                // Write results
                for (Nucleus nuc : nucleus) {
                    outPutResults.write(rootName+"\t"+nuc.getIndex()+"\t"+nuc.getNucVol()+"\t"+nuc.getNucPmlFoci()+"\t"+nuc.getNucPmlVol()+"\t"+
                    nuc.getNucPmlFociInt()+"\t"+nuc.getNucPartnerFoci()+"\t"+nuc.getNucPartnerVol()+"\t"+nuc.getNucPartnerFociInt()+"\t"+
                    nuc.getNucPmlInt()+"\t"+nuc.getNucPartnerInt()+"\t"+nuc.getNucPartnerPmlColocFoci()+"\t"+nuc.getNucPartnerPmlColocVolFoci()+"\n");
                    outPutResults.flush();
                }
                
                // save images
                tools.saveImgObjects(nucPop, pmlFociPop, partnerFociPop, rootName, imgDAPI, outDirResults);
                tools.flush_close(imgDAPI);
        
                
            }
            outPutResults.close();
        } catch (IOException | FormatException | DependencyException | ServiceException | io.scif.DependencyException ex) {
            Logger.getLogger(PML_Partners_FixedCells.class.getName()).log(Level.SEVERE, null, ex);
        }
        IJ.showStatus("Process done");
    }    
}    
