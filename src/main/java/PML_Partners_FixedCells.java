/*
 * Find nuclei and count PML dots inside 
 * If third channel, detect Partner dots and compute their colocalization with PML dots
 * Author: Philippe Mailly
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
    private boolean canceled = false;
    private String imageDir = "";
    public String outDirResults = "";
    private BufferedWriter outPutResults;
    
    
    public void run(String arg) {
        try {
            if (canceled) {
                IJ.showMessage("Plugin canceled");
                return;
            }
            if ((!tools.checkInstalledModules()) || (!tools.checkStarDistModels())) {
                return;
            }
            
            imageDir = IJ.getDirectory("Choose directory containing image files...");
            if (imageDir == null) {
                return;
            }   
            // Find images with file_ext extension
            String file_ext = tools.findImageType(new File(imageDir));
            ArrayList<String> imageFiles = tools.findImages(imageDir, file_ext);
            if (imageFiles == null) {
                IJ.showMessage("Error", "No images found with " + file_ext + " extension");
                return;
            }   
            
            // Create output folder
            outDirResults = imageDir + File.separator + "Results" + File.separator;
            File outDir = new File(outDirResults);
            if (!Files.exists(Paths.get(outDirResults))) {
                outDir.mkdir();
            }
            // Write header in results file
            String header = "Image name\tNucleus ID\tNucleus volume (µm3)\tPML foci nb\tPML foci sum volume\tPML foci sum intensity\t"
                    + "PML diffuse sum intensity\tPML F-function-related spatial distribution index\tPartner foci nb\tPartner foci sum volume\tPartner foci sum intensity\t"
                    + "Partner diffuse sum intensity\tPartner F-function-related spatial distribution index\tPML-positive Partner foci nb\t"
                    + "PML-positive Partner foci sum volume\tPML-positive Partner foci volume overlap percentage (%)\n";
            FileWriter fwResults = new FileWriter(outDirResults + "results.xls", false);
            outPutResults = new BufferedWriter(fwResults);
            outPutResults.write(header);
            outPutResults.flush();
                      
            // Create OME-XML metadata store of the latest schema version
            ServiceFactory factory;
            factory = new ServiceFactory();
            OMEXMLService service = factory.getInstance(OMEXMLService.class);
            IMetadata meta = service.createOMEXMLMetadata();
            ImageProcessorReader reader = new ImageProcessorReader();
            reader.setMetadataStore(meta);
            reader.setId(imageFiles.get(0));
            
            // Find image calibration
            tools.cal = tools.findImageCalib(meta);
            
            // Find channel names
            String[] chsName = tools.findChannels(imageFiles.get(0), meta, reader);

            // Channels dialog
            String[] channels = tools.dialog(chsName);
            if (channels == null) {
                IJ.showStatus("Plugin cancelled");
                return;
            }         
            
            
            for (String f : imageFiles) {
                String rootName = FilenameUtils.getBaseName(f);
                tools.print("--- ANALYZING IMAGE " + rootName + " ------");
                reader.setId(f);
                
                ImporterOptions options = new ImporterOptions();
                options.setId(f);
                options.setSplitChannels(true);
                options.setQuiet(true);
                options.setColorMode(ImporterOptions.COLOR_MODE_GRAYSCALE);
                
                // Open DAPI channel
                tools.print("- Analyzing " + tools.channelNames[0] + " channel -");
                int indexCh = ArrayUtils.indexOf(chsName, channels[0]);
                ImagePlus imgDAPI = BF.openImagePlus(options)[indexCh];
                
                // Find DAPI nuclei with StarDist
                System.out.println("Finding " + tools.channelNames[0] + " nuclei....");
                ArrayList<Nucleus>  nuclei = new ArrayList();
                Objects3DIntPopulation nucPop = tools.stardistNucleiPop(imgDAPI, nuclei);
                System.out.println(nucPop.getNbObjects() + " " + tools.channelNames[0] + " nuclei found");

                // Open PML channel
                tools.print("- Analyzing " + tools.channelNames[1] + " channel -");
                indexCh = ArrayUtils.indexOf(chsName, channels[1]);
                ImagePlus imgPML = BF.openImagePlus(options)[indexCh];
                
                // Find PML foci with StarDist
                Objects3DIntPopulation pmlFociPop = tools.stardistFociInCellsPop(imgPML, nucPop, nuclei, "PML");
                System.out.println(pmlFociPop.getNbObjects() + " PML foci colocalized with " + tools.channelNames[0] + " nuclei");
                tools.flush_close(imgPML);
                
                // If Partner channel exists...
                Objects3DIntPopulation partnerFociPop = new Objects3DIntPopulation();
                if (chsName.length > 2) {
                    // Open Partner channel
                    tools.print("- Analyzing " + tools.channelNames[2] + " channel -");
                    indexCh = ArrayUtils.indexOf(chsName, channels[2]);
                    ImagePlus imgPartner = BF.openImagePlus(options)[indexCh];
                    // Find Partner foci with StarDist
                    partnerFociPop = tools.stardistFociInCellsPop(imgPartner, nucPop, nuclei, "Partner");
                    System.out.println(partnerFociPop.getNbObjects() + " Partner foci colocalized with " + tools.channelNames[0] + " nuclei");
                    tools.flush_close(imgPartner);
                    // Colocalization between PML and Partner foci
                    tools.findColocPartnerPml(nucPop.getNbObjects(), partnerFociPop, pmlFociPop, nuclei);
                }
                
                // Save images
                tools.saveImgObjects(nucPop, pmlFociPop, partnerFociPop, imgDAPI, rootName, outDirResults);
                tools.flush_close(imgDAPI);
                
                // Write results
                for (Nucleus nuc: nuclei) {
                    outPutResults.write(rootName+"\t"+nuc.getIndex()+"\t"+nuc.getNucVol()+"\t"+nuc.getNucPmlFoci()+"\t"+nuc.getNucPmlVol()+"\t"+
                    nuc.getNucPmlFociInt()+"\t"+nuc.getNucPmlInt()+"\t"+nuc.getNucPmlSdiF()+"\t"+nuc.getNucPartnerFoci()+"\t"+nuc.getNucPartnerVol()+"\t"+
                    nuc.getNucPartnerFociInt()+"\t"+nuc.getNucPartnerInt()+"\t"+nuc.getNucPartnerSdiF()+"\t"+nuc.getNucPartnerPmlColocFoci()+"\t"+
                            nuc.getNucPartnerPmlColocVolFoci()+"\t"+nuc.getNucPartnerPmlColocVolOverlap()+"\n");
                    outPutResults.flush();
                }
            }
            outPutResults.close();
        } catch (IOException | FormatException | DependencyException | ServiceException | io.scif.DependencyException ex) {
            Logger.getLogger(PML_Partners_FixedCells.class.getName()).log(Level.SEVERE, null, ex);
        }
        tools.print("--- All done! ---");
    }    
}    
