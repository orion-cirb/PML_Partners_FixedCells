# PML_Partners_FixedCells 

* **Developed for:** Pierre
* **Team:** De Thé
* **Date:** October 2022
* **Software:** Fiji

### Images description

3D images taken with a x60 objective

3 channels:
  1. *Alexa Fluor 647:* Partner foci (non mandatory)
  2. *Alexa Fluor 568:* PML foci
  3. *DAPI:* DAPI nuclei

### Plugin description

* Find DAPI nuclei with StarDist
* Detect PML foci in each nucleus 
* If Partner channel is provided, detect Partner foci in each nucleus and compute their colocalization with PML foci

### Dependencies

* **3DImageSuite** Fiji plugin
* **CLIJ** Fiji plugin
* **StarDist** conda environment + *StandardFluo.zip* and *pmls2.zip* (homemade) models

### Version history

Version 1 released on October 26, 2022.
