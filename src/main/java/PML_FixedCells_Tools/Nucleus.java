package PML_FixedCells_Tools;

/**
 * @author phm
 */
public class Nucleus {
    
    // Nucleus index
    private int index;
    // Nucleus volume
    private double nucVol;
    
    // PML foci number
    private int nucPmlFoci;
    // PML foci volume
    private double nucPmlVol;
    // PML foci intensity
    private double nucPmlFociInt;
    // PML diffuse intensity
    private double nucPmlInt;
    // PML distribution coefficient
    private Double nucPmlSdiF;
    
    // Partner foci number
    private int nucPartnerFoci;   
    // Partner foci volume
    private double nucPartnerVol;
    // Partner foci intensity
    private double nucPartnerFociInt;
    // Partner diffuse intensity
    private double nucPartnerInt;
    // Partner distribution coefficient
    private Double nucPartnerSdiF;
    
    // Number of partner foci colocalizng with PML foci
    private int nucPartnerPmlColocFoci;
    // Volume of partner foci colocalizng with PML foci
    private double nucPartnerPmlColocVolFoci;
    
	
    public Nucleus(int index, double nucVol, double nucPmlInt, double nucPartnerInt, int nucPmlFoci, int nucPartnerFoci, double nucPmlVol, 
            double nucPartnerVol, double nucPmlFociInt, double nucPartnerFociInt, int nucPartnerPmlColocFoci, double nucPartnerPmlColocVolFoci,
            Double nucPmlSdiF, Double nucPartnerSdiF) {
        this.index = index;
        this.nucVol = nucVol;
        this.nucPmlInt = nucPmlInt;
        this.nucPartnerInt = nucPartnerInt;
        this.nucPmlFoci = nucPmlFoci; 
        this.nucPartnerFoci = nucPartnerFoci; 
        this.nucPmlVol = nucPmlVol;
        this.nucPartnerVol = nucPartnerVol;
        this.nucPmlFociInt = nucPmlFociInt;
        this.nucPartnerFociInt = nucPartnerFociInt;
        this.nucPartnerPmlColocFoci = nucPartnerPmlColocFoci;
        this.nucPartnerPmlColocVolFoci = nucPartnerPmlColocVolFoci;
        this.nucPmlSdiF = nucPmlSdiF;
        this.nucPartnerSdiF = nucPartnerSdiF;
    }
        
        public void setIndex(int index) {
            this.index = index;
	}
        
        public void setNucVol(double nucVol) {
            this.nucVol = nucVol;
	}
        
        public void setNucPmlInt(double nucPmlInt) {
            this.nucPmlInt = nucPmlInt;
	}
        
        public void setNucPartnerInt(double nucPartnerInt) {
            this.nucPartnerInt = nucPartnerInt;
	}
        
        public void setNucPmlFoci(int nucPmlFoci) {
            this.nucPmlFoci = nucPmlFoci;
	}
        
        public void setNucPartnerFoci(int nucPartnerFoci) {
            this.nucPartnerFoci = nucPartnerFoci;
	}
        
        public void setNucPmlVol(double nucPmlVol) {
            this.nucPmlVol = nucPmlVol;
	}
        
        public void setNucPartnerVol(double nucPartnerVol) {
            this.nucPartnerVol = nucPartnerVol;
	}
        
        public void setNucPmlFociInt(double nucPmlFociInt) {
            this.nucPmlFociInt = nucPmlFociInt;
        }
        
        public void setNucPartnerFociInt(double nucPartnerFociInt) {
            this.nucPartnerFociInt = nucPartnerFociInt;
        }
        
        public void setNucPartnerPmlColocFoci(int nucPartnerPmlColocFoci) {
            this.nucPartnerPmlColocFoci = nucPartnerPmlColocFoci;
	}
        
        public void setNucPartnerPmlColocVolFoci(double nucPartnerPmlColocVolFoci) {
            this.nucPartnerPmlColocVolFoci = nucPartnerPmlColocVolFoci;
	}
        
        public void setNucPmlSdiF(Double nucPmlSdiF) {
            this.nucPmlSdiF = nucPmlSdiF;
        }
        
        public void setNucPartnerSdiF(Double nucPartnerSdiF) {
            this.nucPartnerSdiF = nucPartnerSdiF;
        }
        
        
        public int getIndex() {
            return index;
        }
        
        public double getNucVol() {
            return nucVol;
        }
                
        public double getNucPmlInt() {
            return nucPmlInt;
        }
                
        public double getNucPartnerInt() {
            return nucPartnerInt;
        }
                
        public int getNucPmlFoci() {
            return nucPmlFoci;
        }
                
        public int getNucPartnerFoci() {
            return nucPartnerFoci;
        }
        
         public double getNucPmlVol() {
            return nucPmlVol;
	}
        
        public double getNucPartnerVol() {
            return nucPartnerVol;
	}
        
        public double getNucPmlFociInt() {
            return nucPmlFociInt;
        }
        
        public double getNucPartnerFociInt() {
            return nucPartnerFociInt;
        }
        
        public int getNucPartnerPmlColocFoci() {
            return nucPartnerPmlColocFoci;
        }
                
        public double getNucPartnerPmlColocVolFoci() {
            return nucPartnerPmlColocVolFoci;
	}
        
       public Double getNucPmlSdiF() {
            return nucPmlSdiF;
        }
        
        public Double getNucPartnerSdiF() {
            return nucPartnerSdiF;
        }
}
