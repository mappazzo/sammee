/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package ee.sammee;

import java.io.*;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Carlito
 */
public class SAMmodel {
    
    private int noCells;
    private int timeStep;
    private int noTimesteps;
    private int rainStep;
    private double maxY;
    private double minY;
    
    // *******************
    // Future modification: add variable boundary condition
    // *******************
    // private double boundTS;
    // private double[] boundL;
    // private double[] boundR;
    
    private double[] boundConst;
    private double[] rainData;
    private double[] rainModel;
    private double constIL = -1;
    private double constPL = -1;
    private double constSY = -1;
    private double constK = -1;
    private double[] modelX;
    private double[] modelDX;
    private double[] modelK;
    private double[] cellWidth;
    private double[] modelImp;
    private double[] modelSurf;
    private double[] cellCatch;
    private double[] cellInfLimit;
    private double[] cellIL;
    private double[] cellPL;
    private double[] cellSY;
    private double[] cellK;
    private double[][] modelWL;
    private double[] iniWL;
    private double[] TsIL; // timestep Initial Loss
    private double[] TsR; // timestep Storm Runoff
    private double[] TsFL; // timestep Flux Left
    private double[] TsFR; // timestep Flux Right
    private double[] TsSD; // timestep Surface Drainage
    private double[] maxWL;
    private boolean monitorLoad = false;
    private boolean monitorSteps = false;
    private boolean monitorOutSummary = true;
    private boolean monitorOutPeriods = true;
    private boolean monitorOutDetail = false;
    private boolean outputOpen = false;
    private final DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
    private Calendar startDate = Calendar.getInstance();
    private PrintWriter outFile;
    
    // model control functions
    public void setOutput(boolean loadOut, boolean stepsOut, boolean summOut, boolean periodsOut, boolean detailOut) {
        monitorLoad = loadOut;
        monitorSteps = stepsOut;
        monitorOutSummary = summOut;
        monitorOutDetail = detailOut;
        monitorOutPeriods = periodsOut;
    }
    public void setStartDate(Date sDate) {
        startDate.setTime(sDate);
    }
    public boolean iniOutput(Path outputFile) {
        
        Calendar currentDate = Calendar.getInstance();
        try {
            String outputString = outputFile.toString();
            outFile = new PrintWriter(new FileWriter(outputString));
            outFile.println("SAMMEE-1D Output file");
            outFile.println("created: " + dateFormat.format(currentDate.getTime()));
            outputOpen = true;
            return true;
        } catch (IOException ex) {
            Logger.getLogger(SAMmodel.class.getName()).log(Level.SEVERE, null, ex);
            System.out.print("could not open output file");
            return false;
        }
    }
    public boolean readParameterFile(Path paramFile) throws IOException {
        
        outFile.println("_______________________________");
        outFile.println("COMMENCE load parameter file: " + paramFile.getFileName().toString());
        if(monitorLoad) outFile.println();
        
        String fileHeader = "sammee-1d parameter file";
        Charset charset = Charset.forName("US-ASCII"); 
        boolean success = true;
        
        try (BufferedReader reader = Files.newBufferedReader(paramFile, charset)) {
                String line;
                String[] lineArray;
                
                line = reader.readLine();
                lineArray = line.split(",");
                    
                if (!lineArray[0].equals(fileHeader)) {
                    outFile.println("Parameter file header incorrect or not found");
                    success = false;
                    return success;
                }
         
                int lineCount = 0;
                while (lineCount < 100) {
                    lineCount++;
                    if((line = reader.readLine()) != null){
                        
                        lineArray = line.split(",");
                        if(lineArray.length == 0) continue;
                        
                        if("timestep".equals(lineArray[0])) {
                            timeStep = Integer.parseInt(lineArray[1]);
                        }    
                        if("number timesteps".equals(lineArray[0])) {
                            noTimesteps = Integer.parseInt(lineArray[1]);
                        }
                        if("const SY".equals(lineArray[0])) {
                            constSY = Double.parseDouble(lineArray[1]);
                            if(monitorLoad){outFile.println("Set constant SY (m3/m3) = " + constSY);}
                        }
                        if("const K".equals(lineArray[0])) {
                            constK = Double.parseDouble(lineArray[1]);
                            if(monitorLoad){outFile.println("Set constant hydraulic conductivity 'K' (m/day) = " + constK);}
                        }
                        if("const IL".equals(lineArray[0])) {
                            constIL = Double.parseDouble(lineArray[1]);
                            if(monitorLoad){outFile.println("Set constant initial loss (mm/day) = " + constIL);}
                        }
                        if("const PL".equals(lineArray[0])) {
                            constPL = Double.parseDouble(lineArray[1]);
                            if(monitorLoad){outFile.println("Set constant proportional loss (mm/mm) = " + constPL);}
                        }
                        if("bound const WL".equals(lineArray[0])) {
                            boundConst = new double[2];
                            boundConst[0] = Double.parseDouble(lineArray[1]);
                            boundConst[1] = Double.parseDouble(lineArray[2]);
                            if(monitorLoad){
                                outFile.println("Set constant boundary condition (mAD) = ");
                                printArray(boundConst);
                            }
                        }
                    }
                }
                
                // check data load
                if(monitorLoad) outFile.println();
                if(timeStep == 0) {
                    success = false;
                    outFile.println("ERROR Paramater file - failed to load model timestep");}
                if(noTimesteps == 0) {
                    success = false;
                    outFile.println("ERROR Paramater file - failed to load number of timesteps");}
                if(boundConst == null) {
                    success = false;
                    outFile.println("ERROR Paramater file - failed to load boundary condition WL");}
                if(!success) return success;
                
        } catch (IOException x) {
            System.err.format("IOException: %s%n", x);
            outFile.println();
            outFile.println("ERROR Paramater file - could not open file");
            success = false;
            return success;
        }
        
        outFile.println("Parameter file load completed successfully");
        
        return success;
    }    
    public boolean readSetupFile(Path setupFile) throws IOException {
        
        
        outFile.println("_______________________________");
        outFile.println("COMMENCE load setup file: " + setupFile.getFileName().toString());
        if(monitorLoad) outFile.println();
        
        String fileHeader = "sammee-1d setup file";
        Charset charset = Charset.forName("US-ASCII");
        boolean success = true;
                        
        try (BufferedReader reader = Files.newBufferedReader(setupFile, charset)) {
                String line;
                String[] lineArray;
                
                line = reader.readLine();
                lineArray = line.split(",");
                
                if (!lineArray[0].equals(fileHeader)) {
                    outFile.println("FAILURE Setup file; incorrect file header");
                    success = false;
                    return success;
                }
         
                int lineCount = 0;
                while (lineCount < 100) {
                    lineCount++;
                    if((line = reader.readLine()) != null){
                        
                        lineArray = line.split(",");
                        if(lineArray.length == 0) continue;
                        
                        if("model x".equals(lineArray[0])) {
                            if(noCells == 0) setNoCells(lineArray.length - 2);
                            if(lineArray.length == noCells + 2) {
                                modelX = new double[noCells+1];
                                for(int i=0; i<noCells+1; i++) modelX[i] = Double.parseDouble(lineArray[i+1]);
                                if(monitorLoad){
                                    outFile.println("Loaded model position 'X' data (m) = ");
                                    printArray(modelX);
                                }
                            }
                        }

                        if("model impervious".equals(lineArray[0])) {
                            if(noCells == 0) setNoCells(lineArray.length - 3);
                            if(lineArray.length == noCells + 3) {
                                modelImp = new double[noCells+2];
                                for(int i=0; i<noCells+2; i++) modelImp[i] = Double.parseDouble(lineArray[i+1]);
                                minY = doubleArrayMath("min",modelImp,0,0);
                                if(monitorLoad){
                                    outFile.println("Loaded model impervious data (mAD) = ");
                                    printArray(modelImp);
                                }
                            }
                        }

                        if("model surface".equals(lineArray[0])) {
                            if(noCells == 0) setNoCells(lineArray.length - 3);
                            if(lineArray.length == noCells + 3) {
                                modelSurf = new double[noCells+2];
                                for(int i=0; i<noCells+2; i++) modelSurf[i] = Double.parseDouble(lineArray[i+1]);
                                maxY = doubleArrayMath("max",modelSurf,0,0);
                                if(monitorLoad){
                                    outFile.println("Loaded model surface data (mAD) = ");
                                    printArray(modelSurf);
                                }
                            }
                        }

                        if("cell initial WL".equals(lineArray[0])) {
                            if(noCells == 0) setNoCells(lineArray.length - 1);
                            if(lineArray.length == noCells + 1) {
                                iniWL = new double[noCells+2];
                                iniWL[0] = boundConst[0];
                                iniWL[noCells+1] = boundConst[1];

                                for(int i=0; i<noCells; i++) iniWL[i+1] = Double.parseDouble(lineArray[i+1]);
                                if(monitorLoad){
                                    outFile.println("Loaded cell initial water level; iniWL (mAD) = ");
                                    printArray(iniWL);
                                }
                            }
                        }

                        if("cell SY".equals(lineArray[0])) {
                            if(noCells == 0) setNoCells(lineArray.length - 1);
                            if(lineArray.length == noCells + 1) {
                                cellSY = new double[noCells];
                                for(int i=0; i<noCells; i++) cellSY[i] = Double.parseDouble(lineArray[i+1]);
                                if(monitorLoad){
                                    outFile.println("Loaded cell specific yeild data (m3/m3) = ");
                                    printArray(cellSY);
                                }
                            }
                        }
                        
                        if("cell IL".equals(lineArray[0])) {
                            if(noCells == 0) setNoCells(lineArray.length - 1);
                            if(lineArray.length == noCells + 1) {
                                cellIL = new double[noCells];
                                for(int i=0; i<noCells; i++) cellIL[i] = Double.parseDouble(lineArray[i+1]);
                                if(monitorLoad){
                                    outFile.println("Loaded cell specific initial loss data (mm/day) = ");
                                    printArray(cellIL);
                                }
                            }
                        }
                        
                        if("cell PL".equals(lineArray[0])) {
                            if(noCells == 0) setNoCells(lineArray.length - 1);
                            if(lineArray.length == noCells + 1) {
                                cellPL = new double[noCells];
                                for(int i=0; i<noCells; i++) cellPL[i] = Double.parseDouble(lineArray[i+1]);
                                if(monitorLoad){
                                    outFile.println("Loaded cell specific proportional loss data (m/m) = ");
                                    printArray(cellPL);
                                }
                            }
                        }
                        
                        if("cell K".equals(lineArray[0])) {
                            if(noCells == 0) setNoCells(lineArray.length - 1);
                            if(lineArray.length == noCells + 1) {
                                cellK = new double[noCells];
                                for(int i=0; i<noCells; i++) cellK[i] = Double.parseDouble(lineArray[i+1]);
                                if(monitorLoad){
                                    outFile.println("Loaded cell hydraulic conductivity 'K' data (m/day) = ");
                                    printArray(cellK);
                                }
                            }
                        }

                        if("cell catch".equals(lineArray[0])) {
                            if(noCells == 0) setNoCells(lineArray.length - 1);
                            if(lineArray.length == noCells + 1) {
                                cellCatch = new double[noCells];
                                for(int i=0; i<noCells; i++) cellCatch[i] = Double.parseDouble(lineArray[i+1]);
                                if(monitorLoad){
                                    outFile.println("Loaded cell catchment areas (m2) = ");
                                    printArray(cellCatch);
                                }
                            }
                        }

                        if("cell inf limit".equals(lineArray[0])) {
                            if(noCells == 0) setNoCells(lineArray.length - 1);
                            if(lineArray.length == noCells + 1) {
                                cellInfLimit = new double[noCells];
                                for(int i=0; i<noCells; i++) cellInfLimit[i] = Double.parseDouble(lineArray[i+1]);
                                if(monitorLoad){
                                    outFile.println("Loaded cell infiltration limits (mm/hr) = ");
                                    printArray(cellInfLimit);
                                }
                            }
                        }
                    }
                }
                    
     // apply constant parameters to cell based arrays
                if(cellSY == null && constSY >= 0){
                    cellSY = new double[noCells];
                    for(int i=0; i<noCells; i++) cellSY[i] = constSY;
                    if(monitorLoad){
                        outFile.println("Set CellSY from constant SY = ");
                        printArray(cellSY);
                    }
                }
                if(cellK == null && constK >= 0){
                    cellK = new double[noCells];
                    for(int i=0; i<noCells; i++) cellK[i] = constK;
                    if(monitorLoad){
                        outFile.println("Set CellK from constant K = ");
                        printArray(cellK);
                    }
                }
                if(cellIL == null && constIL >= 0){
                    cellIL = new double[noCells];
                    for(int i=0; i<noCells; i++) cellIL[i] = constIL;
                    if(monitorLoad){
                        outFile.println("Set CellIL from constant IL = ");
                        printArray(cellIL);
                    }
                }
                if(cellPL == null && constPL >= 0){
                    cellPL = new double[noCells];
                    for(int i=0; i<noCells; i++) cellPL[i] = constPL;
                    if(monitorLoad){
                        outFile.println("Set CellPL from constant PL = ");
                        printArray(cellPL);
                    }
                }
                
      // check minimum data requirements
                if(monitorLoad) {
                    outFile.println();
                    outFile.println("Finished reading input file");
                }
                    
                if(modelX == null) {
                    success = false;
                    outFile.println("ERROR in Setup File: missing position 'X' data");}
                if(modelImp == null) {
                    success = false;
                    outFile.println("ERROR in Setup File: missing impervious layer data");}
                if(modelSurf == null) {
                    success = false;
                    outFile.println("ERROR in Setup File: missing surface data");}
                if(cellK == null) {
                    success = false;
                    outFile.println("ERROR in Setup File: missing K data");}
                if(cellSY == null) {
                    success = false;
                    outFile.println("ERROR in Setup File: missing SY data");}
                if(cellPL == null) {
                    success = false;
                    outFile.println("ERROR in Setup File: missing PL data");}
                if(cellIL == null) {
                    success = false;
                    outFile.println("ERROR in Setup File: missing IL data");}
                if(!success) return success;
                                     
      // fill missing data with default values and align parameters          
                if(monitorLoad) {
                    outFile.println();
                    outFile.println("Calculating additional arrays and filling missing data.....");
                }
                
                
                // setup cell width
                cellWidth = new double[noCells];
                for(int i=0; i<noCells; i++) cellWidth[i] = modelX[i+1]-modelX[i];
                if(monitorLoad){
                    outFile.println("Calculated cell width = ");
                    printArray(cellWidth);
                }
                
                // setup model dX
                modelDX = new double[noCells+1];
                for(int i=0; i<noCells; i++) modelDX[i] = cellWidth[i]/2;
                for(int i=1; i<noCells+1; i++) modelDX[i] = modelDX[i] + cellWidth[i-1]/2;
                if(monitorLoad){
                    outFile.println("Calculated model spacing 'dX' = ");
                    printArray(modelDX);
                }
                // setup model K; distance weighted average of contributing cells
                modelK = new double[noCells+1];
                for(int i=0; i<noCells; i++) modelK[i] = cellK[i]*cellWidth[i]/2;
                for(int i=1; i<noCells+1; i++) modelK[i] = modelK[i] + cellK[i-1]*cellWidth[i-1]/2;
                for(int i=0; i<noCells+1; i++) modelK[i] = modelK[i]/modelDX[i];
                if(monitorLoad){
                    outFile.println("Calculated model hydraulic condictivity 'K' = ");
                    printArray(modelK);
                }
                // adjust initial WL
                if(iniWL == null) {
                    iniWL = new double[noCells+2];
                    iniWL[0] = boundConst[0];
                    iniWL[noCells+1] = boundConst[1];
                    for(int i=1; i<=noCells; i++) iniWL[i] = modelImp[i]+0.05;
                    if(monitorLoad){
                        outFile.println("Initial WL for cells not defined; set at impervious layer = ");
                        printArray(iniWL);
                    }
                } else {
                    for(int i=0; i<noCells+2; i++) {
                        if(iniWL[i] < modelImp[i]+0.05){
                            iniWL[i] = modelImp[i]+0.05;
                            if(monitorLoad) outFile.println("Adjusted initial WL at cell "
                                    + (i+1) + " to be higher than impervious layer; new iniWL = " + iniWL[i]);
                        }
                    }
                }
                
                // transfer initial water levels to timestep array
                modelWL = new double[noTimesteps+1][noCells+2]; 
                for(int i=0; i<noCells+2; i++) modelWL[0][i] = iniWL[i];
                
                // default cell infiltration limit
                if(cellInfLimit == null) {
                    cellInfLimit = new double[noCells];
                    for(int i=0; i<noCells; i++) cellInfLimit[i] = 1000;
                    if(monitorLoad) outFile.println("No infiltration limit set; adopting default 1000 mm/day");
                }
                
                // default cell catchment
                if(cellCatch == null) {
                    cellCatch = new double[noCells];
                    for(int i=0; i<noCells; i++) cellCatch[i] = cellWidth[i];
                    if(monitorLoad){outFile.println("No catchment data set; adopting cell width");}
                }
                   
        } catch (IOException x) {
            System.err.format("IOException: %s%n", x);
            outFile.println();
            outFile.println("ERROR; could not find or open Setup file");
            success = false;
            return success;
        } 
        
        if(monitorLoad) outFile.println();
        outFile.println("Setup file load completed successfully");
        return success;
         
    }
    public boolean readRainFile(Path rainFile) throws IOException {
        
        String fileHeader = "sammee-1d rain file";
        Charset charset = Charset.forName("US-ASCII");
        boolean success = true;
        double multipTS;
        int rainIndex;
        int noRain;
        
        outFile.println("_______________________________");
        outFile.println("COMMENCE load rainfall file: " + rainFile.getFileName().toString());
        if(monitorLoad) outFile.println();

        if (noCells == 0) {
            outFile.println("FAILURE Rain file; number of cells not defined");
            success = false;
            return success;
        }
                        
        try (BufferedReader reader = Files.newBufferedReader(rainFile, charset)) {
                String line;
                String[] lineArray;
                
                line = reader.readLine();
                lineArray = line.split(",");
                
                if (!lineArray[0].equals(fileHeader)) {
                    outFile.println("FAILURE Rain file; incorrect file header");
                    success = false;
                    return success;
                }
                
            try {
                startDate.setTime(dateFormat.parse("2000/01/01 00:01"));
            } catch (ParseException ex) {
                outFile.println("INTERNAL ERROR; parse fixed date reading rainfall file, refer problem to administrator");
            }
                
                //System.out.println("LOAD RAINFALL FILE");
                int lineCount = 0;
                while (lineCount < 100) {
                    lineCount++;
                    if((line = reader.readLine()) != null){
                        lineArray = line.split(",");
                        if(lineArray.length == 0) continue;
                        
                        if("rain TS".equals(lineArray[0])) {
                            //System.out.print(lineArray[0]);System.out.print(" '" + lineArray[1] + "'\n");
                            rainStep = Integer.parseInt(lineArray[1]);
                            if(monitorLoad) outFile.println("Loaded rainfall timestep = " + rainStep);
                        }
                        
                        if("rain Start".equals(lineArray[0])) {
                            //System.out.print(lineArray[0]);System.out.print(" '" + lineArray[1] + "'\n");
                            try {
                                startDate.setTime(dateFormat.parse(lineArray[1]));
                                if(monitorLoad) outFile.println("Loaded rainfall start date / time = " + rainStep);
                            } catch (ParseException ex) {
                                try {
                                    outFile.println("WARNING Rain file; rain start time not in correct format 'yyyy/MM/dd HH:mm', using default time");
                                    startDate.setTime(dateFormat.parse("1982/07/31 15:01"));
                                } catch (ParseException ex1) {
                                    outFile.println("INTERNAL ERROR; parse fixed date reading rainfall file, refer problem to administrator");
                                }
                            }
                        }

                        if("rain".equals(lineArray[0])) {

                            noRain = lineArray.length - 1;
                            if(noRain != 0){
                                rainData = new double[noRain];
                                for(int i=0; i<noRain; i++) rainData[i] = Double.parseDouble(lineArray[i+1]);
                                if(monitorLoad){
                                    outFile.println("Loaded rainfall data (mm) = ");
                                    printArray(rainData);
                                }
                            }
                        }
                    }
                }
                //System.out.print("startTime: ");System.out.print(dateFormat.format(startDate.getTime()) + "\n");
                
                if(rainStep < timeStep){
                    outFile.println("FAILURE Rain file; rain timestep must be greater than model timestep");
                    success = false;
                    return success;
                }
                            
                multipTS = rainStep / timeStep;
                rainModel = new double[noTimesteps];
                
                rainIndex = 0;                
                for(int i=0; i<noTimesteps; i++) {
                    rainIndex = (int)Math.floor(i/multipTS);
                    if(rainIndex >= rainData.length){
                        rainModel[i] = 0.0;
                    } else {
                        rainModel[i]=rainData[rainIndex]/multipTS;
                    }
                }
                                
                if(rainIndex >= rainData.length){
                    outFile.println("WARNING Rain file; rain data stops before last timestep");
                }
                
                // check data load
                if(rainData == null) {
                    success = false;
                    outFile.println("FAILURE Rain file; failed to load rain data");
                }
                   
        } catch (IOException x) {
            System.err.format("IOException: %s%n", x);
            outFile.println("FAILURE; rainfall file does not exist");
            success = false;
            return success;
        } 
        
        if(monitorLoad) outFile.println();
        outFile.println("Rainfall file loaded successfully");
        return success;
    }
    public boolean runModel() {

        // initialise variables
        
        boolean success = true;
        
        double infLimit;
        double excessRain;
        double runoffStorm;
        double excessInfilt;
        double surfDrain;
        double fluxDepth;
        double dH;
        double[] cellRunoff = new double[noCells];
        double[] cellFlux = new double[noCells];
        double[] cellDrain = new double[noCells];
        double[] infDay = new double[noCells];
        double[] infVol = new double[noCells];
        double[] modelFlux = new double[noCells+1];
        double timeStepsDay = 1440 / timeStep;
        int modelDay = -1;
        double rainDay = 0;
        TsIL = new double[noTimesteps];
        TsR = new double[noTimesteps];
        TsSD = new double[noTimesteps];
        TsFL = new double[noTimesteps];
        TsFR = new double[noTimesteps];
        maxWL = new double[noCells+2];
        
        // write output headers
        outFile.println("_______________________________");
        outFile.println("COMMENCE model run");
        
        if(monitorSteps){
            outFile.println();
            outFile.print("--- Time step results --- \n");
        }
        
        for(int i = 0; i<noTimesteps; i++){
            // initialise runoff total for this timestep
            TsIL[i] = 0;
            TsR[i] = 0;
            TsSD[i] = 0;
            TsFL[i] = 0;
            TsFR[i] = 0;
            
            // check if timestep represents a new day and update daily rainfall total            
            if(modelDay < (int)Math.floor(i / timeStepsDay)) {
                modelDay++;
                rainDay = 0;
                for(int c = 0; c < noCells; c++) infDay[c] = 0;
                if(monitorSteps) outFile.print("\n" + "Day " + modelDay + "\n");
            }
            rainDay = rainDay + rainModel[i];
                    
            // For each cell distribute rainfall to IL, surface flow and infiltration
            for(int c = 0; c < noCells; c++){

                // assign rainfall to IL, surface flow and potential infiltration
                infVol[c] = 0;
                cellRunoff[c] = 0;
                
                if(rainDay >= cellIL[c]){   // if rainfall total has exceeded IL    
                    // calculate storm runoff
                    excessRain = (double)Math.min(rainDay-cellIL[c], rainModel[i]);
                    infVol[c] = cellCatch[c]*excessRain*(1-cellPL[c]); // rainfall availabile for infiltration as function of CL
                    if(infVol[c]<0) infVol[c] = 0; // infiltration is non-zero
                    runoffStorm = cellCatch[c]*excessRain*cellPL[c]; // surface runoff is inverse of infiltration
                    if(runoffStorm > 0) cellRunoff[c] = cellRunoff[c] + runoffStorm; // assign surface runoff to cell running total
                    TsIL[i] = TsIL[i] + (rainModel[i]-excessRain)*cellCatch[c];
                    
                    // calculate infiltration excess 
                    excessInfilt = 0;
                    infDay[c] = infDay[c] + infVol[c];
                    infLimit = cellWidth[c]*cellInfLimit[c];
                    if(infDay[c] > infLimit) {
                        excessInfilt = (double)Math.min(infDay[c]-infLimit, infVol[c]);
                        infVol[c] = infVol[c] - excessInfilt;
                        cellRunoff[c] = cellRunoff[c] + excessInfilt;
                    }
                    TsR[i] = TsR[i] + runoffStorm + excessInfilt;
                    
                } else {
                    TsIL[i] = TsIL[i] + rainModel[i]*cellCatch[c]; // assign any initial loss to running total
                }
                
            }
            
            // Darcy flux calculated in L
            for(int c = 0; c < noCells+1; c++){
                // calculate flux to the left
                fluxDepth = ((modelWL[i][c] - modelImp[c]) + (modelWL[i][c+1] - modelImp[c+1]))/2;
                dH = modelWL[i][c+1]-modelWL[i][c];
                modelFlux[c] = - fluxDepth * modelK[c] * dH / modelDX[c] * 1000 / timeStepsDay;
            }
            
            // Adjust flux if WL in Cell at impervious layer
            for(int c = 0; c < noCells; c++) {
                if(modelWL[i][c+1]-modelImp[c+1] <= 0.01) {
                    if(modelFlux[c] < 0) modelFlux[c] = 0;
                    if(modelFlux[c+1] > 0) modelFlux[c+1] = 0;
                }
            }
            
            // calculate new water levels
            modelWL[i+1][0] = boundConst[0];
            modelWL[i+1][noCells+1] = boundConst[1];
            for(int c = 0; c<noCells; c++){
                cellFlux[c] = modelFlux[c] - modelFlux[c+1] + infVol[c];
                modelWL[i+1][c+1] = modelWL[i][c+1] + cellFlux[c] / 1000 / cellWidth[c] / cellSY[c];
            }
            
            // calculate flux from boundaries
            TsFL[i] = -modelFlux[0];
            TsFR[i] = modelFlux[noCells];
            
            // check for surface drainage
            for(int c = 0; c<noCells; c++){
                surfDrain = 0;
                if(modelWL[i+1][c+1]>modelSurf[c+1]){
                    surfDrain = (modelWL[i+1][c+1] - modelSurf[c+1])*cellWidth[c]*cellSY[c]*1000;
                    cellDrain[c] = surfDrain;
                    modelWL[i+1][c+1] = modelSurf[c+1];
                }
                TsSD[i] = TsSD[i] + surfDrain;
            }
            
            // identify if timestep changes maximum water levels
            for(int c = 0; c < noCells+2; c++){
                if(maxWL[c] < modelWL[i+1][c]) maxWL[c] = modelWL[i+1][c];
            }
            
            // Timestep output
            if(monitorSteps){
                outFile.print("Time Step:, " + i + "\n");
                outFile.print("   Rain in this time step [mm]:, " + (float)rainModel[i] + "\n");
                outFile.print("   Rain since start of day [mm]:, " + (float)rainDay + "\n");
                outFile.print("   Darcy Flux [L]:, "); printArray(modelFlux);
                outFile.print("   Net Cell Flux [L]:, "); printArray(cellFlux);
                outFile.print("   Storm Runoff [L]:, "); printArray(cellRunoff);
                outFile.print("   Infiltration [L]:, "); printArray(infVol);
                outFile.print("   Surface Drainage [L]:, "); printArray(cellDrain);
            }
        }
        outFile.println("Model run completed successfully");
            
        // system print output
        if(monitorOutSummary || monitorOutPeriods || monitorOutDetail) {
            outFile.println("_______________________________");
            outFile.println("MODEL RESULTS");
        }
        if(monitorOutSummary) outputResultSummary();
        if(monitorOutPeriods) outputResultPeriods();
        if(monitorOutDetail) outputResultDetail();
        return success;  
    }
    public boolean exportResult(Path outputFile) {
        closeOutput();
        DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm");
        Date date = new Date();
        try {
            String outputString = outputFile.toString();
            outFile = new PrintWriter(new FileWriter(outputString));
            outFile.println("SAMMEE-1D Results Export");
            outFile.println("created: " + dateFormat.format(date));
            outputOpen = true;
            if(monitorOutSummary) outputResultSummary();
            if(monitorOutPeriods) outputResultPeriods();
            if(monitorOutDetail) outputResultDetail();
            closeOutput();
            return true;
        } catch (IOException ex) {
            Logger.getLogger(SAMmodel.class.getName()).log(Level.SEVERE, null, ex);
            System.out.print("could not open output file");
            return false;
        }
    }
    public void outputResultSummary() {
        
            double totalRain = 0;
            double totalIL = 0;
            double totalCatch = 0;
            double totalRunoff = 0;
            double totalSurfDrain = 0;
            double totalFL = 0;
            double totalFR = 0;
            double changeStorage = 0;
            double[] minWaterDepth = new double[noCells];
        
            for(int c = 0; c<noCells; c++){
                totalCatch = totalCatch + cellCatch[c];
                changeStorage = changeStorage + (modelWL[noTimesteps][c+1] - modelWL[0][c+1])*cellWidth[c]*cellSY[c]*1000;
            }
            
            for(int i = 0; i<noTimesteps; i++){
                totalRain = totalRain + rainModel[i] * totalCatch;
                totalRunoff = totalRunoff + TsR[i];
                totalSurfDrain = totalSurfDrain + TsSD[i];
                totalIL = totalIL + TsIL[i];
                totalFL = totalFL + TsFL[i];
                totalFR = totalFR + TsFR[i];
            }
            
            for(int p =0; p<noCells; p++){
                minWaterDepth[p] = modelSurf[p] - maxWL[p+1];
            }
        
            outFile.println();
            outFile.println("OVERALL RESULT");
            outFile.println("--- water balance ---");
            outFile.print("  Rain [L], " + (int)totalRain + "\n");
            outFile.print("  Inital Loss [L], " + (int)totalIL + "\n");
            outFile.print("  Runoff [L], " + (int)totalRunoff + "\n");
            outFile.print("  Groundwater Flux Left [L], " + (int)totalFL + "\n");
            outFile.print("  Groundwater Flux Right [L], " + (int)totalFR + "\n");
            outFile.print("  Surface Drainage [L], " + (int)totalSurfDrain + "\n");
            outFile.print("  Change Storage [L], " + (int)changeStorage + "\n");
            outFile.print("  Balance [L], " + (int)(totalRain - totalRunoff - totalFR - totalFL - totalSurfDrain - totalIL - changeStorage) + "\n");
        
            outFile.println();
            outFile.println("--- peak water levels ---");
            outFile.print("  Cell ID"); for(int c = 1; c<noCells+1; c++) outFile.print(String.format(", %s",c)); outFile.print("\n");
            outFile.print("  Maximum water level [m AD], "); printArray(maxWL,"float",1,noCells);
            outFile.print("  Minimum depth to water [m], "); printArray(minWaterDepth,"float");
    }
    public void outputResultPeriods() {
            double[] monthID = new double[12000];
            double[] monthRain = new double[12000];
            double[] monthIL = new double[12000];
            double[] monthRunoff = new double[12000];
            double[] monthSurfDrain = new double[12000];
            double[] monthFL = new double[12000];
            double[] monthFR = new double[12000];
            double[] monthEndStorage = new double[12000];
            double[] monthChangeStorage = new double[12000];
            double[][] monthMaxWaterLevel = new double[12000][noCells];
            double[] monthMinWaterDepth = new double[12000];
            double[] monthBalance = new double[12000];
            
            double[] yrID = new double[1000];
            double[] yrRain = new double[1000];
            double[] yrIL = new double[1000];
            double[] yrRunoff = new double[1000];
            double[] yrSurfDrain = new double[1000];
            double[] yrFL = new double[1000];
            double[] yrFR = new double[1000];
            double[] yrEndStorage = new double[1000];
            double[] yrChangeStorage = new double[1000];
            double[][] yrMaxWaterLevel = new double[1000][noCells];
            double[] yrMinWaterDepth = new double[1000];
            double[] yrBalance = new double[1000];
            
            double totalCatch = 0;
            double initialStorage = 0;
            int mInd = 0;
            int yInd = 0;
            
            Calendar timeStepDate = Calendar.getInstance();
            timeStepDate.setTime(startDate.getTime());
            double tsMonth = timeStepDate.get(Calendar.MONTH);
            double tsYear = timeStepDate.get(Calendar.YEAR);
            yrID[0] = tsYear;
            monthID[0] = tsYear + (tsMonth + 1) / 100;
            //System.out.print("month: ");System.out.print(timeStepDate.get(Calendar.MONTH) + "\n");
            //System.out.print("year: ");System.out.print(timeStepDate.get(Calendar.YEAR) + "\n");
            for(int c = 0; c<noCells; c++){
                totalCatch = totalCatch + cellCatch[c];  
                initialStorage = initialStorage + modelWL[0][c+1]*cellWidth[c]*cellSY[c]*1000;
            }
            yrMinWaterDepth = setArrayValue(yrMinWaterDepth, 999999);
            monthMinWaterDepth = setArrayValue(monthMinWaterDepth, 999999);
          
            for(int i = 0; i<noTimesteps; i++){
                
                for(int c = 0; c<noCells; c++){
                    if(monthMaxWaterLevel[mInd][c] < modelWL[i+1][c+1]) monthMaxWaterLevel[mInd][c] = modelWL[i+1][c+1];
                    if(monthMinWaterDepth[mInd] > modelSurf[c] - modelWL[i+1][c+1]) monthMinWaterDepth[mInd] = modelSurf[c] - modelWL[i+1][c+1];
                    if(yrMaxWaterLevel[yInd][c] < modelWL[i+1][c+1]) yrMaxWaterLevel[yInd][c] = modelWL[i+1][c+1];
                    if(yrMinWaterDepth[yInd] > modelSurf[c] - modelWL[i+1][c+1]) yrMinWaterDepth[yInd] = modelSurf[c] - modelWL[i+1][c+1];
                }
                monthRain[mInd] = monthRain[mInd] + rainModel[i] * totalCatch;
                monthRunoff[mInd] = monthRunoff[mInd] + TsR[i];
                monthSurfDrain[mInd] = monthSurfDrain[mInd] + TsSD[i];
                monthIL[mInd] = monthIL[mInd] + TsIL[i];
                monthFL[mInd] = monthFL[mInd] + TsFL[i];
                monthFR[mInd] = monthFR[mInd] + TsFR[i];
                
                yrRain[yInd] = yrRain[yInd] + rainModel[i] * totalCatch;
                yrRunoff[yInd] = yrRunoff[yInd] + TsR[i];
                yrSurfDrain[yInd] = yrSurfDrain[yInd] + TsSD[i];
                yrIL[yInd] = yrIL[yInd] + TsIL[i];
                yrFL[yInd] = yrFL[yInd] + TsFL[i];
                yrFR[yInd] = yrFR[yInd] + TsFR[i];
                
                // iterate forward to next timestep and test if it is end of the year or month
                timeStepDate.add(Calendar.MINUTE, timeStep);
                if(timeStepDate.get(Calendar.YEAR) != tsYear){
                    // calculate year end storage volumes
                    for(int c = 0; c<noCells; c++){ 
                        yrEndStorage[yInd] = yrEndStorage[yInd] + modelWL[i+1][c+1]*cellWidth[c]*cellSY[c]*1000;
                    }
                    if(yInd == 0) {
                        yrChangeStorage[0] = yrEndStorage[0] - initialStorage;
                    } else {
                        yrChangeStorage[yInd] = yrEndStorage[yInd] - yrEndStorage[yInd-1];
                    }
                    // change year index ready for the next timestep
                    yInd = yInd + 1;
                    tsYear = timeStepDate.get(Calendar.YEAR);
                    //System.out.print("year: ");System.out.print(timeStepDate.get(Calendar.YEAR) + "\n");
                    yrID[yInd] = tsYear;
                }
                if(timeStepDate.get(Calendar.MONTH) != tsMonth){
                    // calculate month end storage volumes
                    for(int c = 0; c<noCells; c++){ 
                        monthEndStorage[mInd] = monthEndStorage[mInd] + modelWL[i+1][c+1]*cellWidth[c]*cellSY[c]*1000;
                    }
                    if(mInd == 0) {
                        monthChangeStorage[0] = monthEndStorage[0] - initialStorage;
                    } else {
                        monthChangeStorage[mInd] = monthEndStorage[mInd] - monthEndStorage[mInd-1];
                    }
                    // change month index ready for the next timestep
                    mInd = mInd + 1;
                    tsMonth = timeStepDate.get(Calendar.MONTH);
                    monthID[mInd] = tsYear + (tsMonth + 1) / 100;
                }
            }
            
            for(int c = 0; c<noCells; c++){ 
                monthEndStorage[mInd] = monthEndStorage[mInd] + modelWL[noTimesteps][c+1]*cellWidth[c]*cellSY[c]*1000;
                monthChangeStorage[mInd] = monthEndStorage[mInd] - monthEndStorage[mInd-1];
                yrEndStorage[yInd] = yrEndStorage[yInd] + modelWL[noTimesteps][c+1]*cellWidth[c]*cellSY[c]*1000;
                yrChangeStorage[yInd] = yrEndStorage[yInd] - yrEndStorage[yInd-1];
            }
            for(int i = 0; i<mInd+1; i++){
                monthBalance[i] = monthRain[i] - monthRunoff[i] - monthFR[i] - monthFL[i] - monthSurfDrain[i] - monthIL[i] - monthChangeStorage[i];
            }
            for(int i = 0; i<yInd+1; i++){
                yrBalance[i] = yrRain[i] - yrRunoff[i] - yrFR[i] - yrFL[i] - yrSurfDrain[i] - yrIL[i] - yrChangeStorage[i];
            }
            
            outFile.println();
            outFile.println("CALENDAR DIVIDED RESULT");
            outFile.println("--- yearly water balance ---");
            outFile.print("  Year [yyyy], "); printArray(yrID,"int", 0, yInd);
            outFile.print("  Rain [L], "); printArray(yrRain,"int", 0, yInd);
            outFile.print("  Inital Loss [L], "); printArray(yrIL,"int", 0, yInd);
            outFile.print("  Runoff [L], "); printArray(yrRunoff,"int", 0, yInd);
            outFile.print("  Groundwater Flux Left [L], "); printArray(yrFL,"int", 0, yInd);
            outFile.print("  Groundwater Flux Right [L], "); printArray(yrFR,"int", 0, yInd);
            outFile.print("  Surface Drainage [L], "); printArray(yrSurfDrain,"int", 0, yInd);
            outFile.print("  Year End Storage [L], "); printArray(yrEndStorage,"int", 0, yInd);
            outFile.print("  Change Storage [L], "); printArray(yrChangeStorage,"int", 0, yInd);
            outFile.print("  Balance [L], "); printArray(yrBalance,"int", 0, yInd);
            outFile.println();
            outFile.println("--- yearly minimum depth to water ---");
            outFile.print("  Year [yyyy], "); printArray(yrID,"%.2f", 0, yInd);
            outFile.print("  Minimum depth to water [m], "); printArray(yrMinWaterDepth,"float", 0, yInd);
            outFile.println();
            outFile.println("--- yearly maximum water level ---");
            outFile.print("  Cell ID"); for(int c = 1; c<noCells+1; c++) outFile.print(String.format(", %s",c)); outFile.print("\n");
            for(int i = 0; i<yInd +1; i++){
                outFile.print("  " + (int)yrID[i] + ", "); printArray2d(yrMaxWaterLevel,"float",1,i);
            }
            
            outFile.println();
            outFile.println("--- monthly water balance ---");
            outFile.print("  Month [yyyy.mm], "); printArray(monthID,"%.2f", 0, mInd);
            outFile.print("  Rain [L], "); printArray(monthRain,"int", 0, mInd);
            outFile.print("  Inital Loss [L], "); printArray(monthIL,"int", 0, mInd);
            outFile.print("  Runoff [L], "); printArray(monthRunoff,"int", 0, mInd);
            outFile.print("  Groundwater Flux Left [L], "); printArray(monthFL,"int", 0, mInd);
            outFile.print("  Groundwater Flux Right [L], "); printArray(monthFR,"int", 0, mInd);
            outFile.print("  Surface Drainage [L], "); printArray(monthSurfDrain,"int", 0, mInd);
            outFile.print("  Change Storage [L], "); printArray(monthChangeStorage,"int", 0, mInd);
            outFile.print("  Balance [L], "); printArray(monthBalance,"int", 0, mInd);
            outFile.println();
            outFile.println("--- monthly minimum depth to water ---");
            outFile.print("  Month [yyyy.mm], "); printArray(monthID,"%.2f", 0, mInd);
            outFile.print("  Minimum depth to water [m], "); printArray(monthMinWaterDepth,"float", 0, mInd);
            outFile.println();
            outFile.println("--- monthly maximum water level ---");
            outFile.print("  Cell ID"); for(int c = 1; c<noCells+1; c++) outFile.print(String.format(", %s",c)); outFile.print("\n");
            for(int i = 0; i<mInd +1; i++){
                outFile.print(String.format("  %.2f, ",monthID[i])); printArray2d(monthMaxWaterLevel,"float",1,i);
            }
    }
    public void outputResultDetail() {
            outFile.println("");
            outFile.println("DETAILED RESULT");
            outFile.println("--- Time step discharge --- ");
            outFile.print("  Runoff, "); printArray(TsR,"float");
            outFile.print("  Surface Drainage, "); printArray(TsSD,"float");
            outFile.print("  Left boundary, "); printArray(TsFL,"float");
            outFile.print("  Right boundary, "); printArray(TsFR,"float");
            outFile.print("\n");
            outFile.println("--- Cell water levels ---");
            outFile.print("  Cell ID, "); for(int c = 1; c<noCells+1; c++) outFile.print(String.format("%s, ",c)); outFile.print("\n");
            for(int i = 0; i<=noTimesteps;i++){
                outFile.print("  , "); printArray2d(modelWL,"float",1,i,1,noCells);
            }
    }
    public boolean closeOutput() {
        if(outputOpen) outFile.close();
        return false;
    }    
    
    // functions to extract model data
    public double[] getBounds() {
        double[] array = {noCells, maxY, minY, modelX[0], modelX[noCells]};
        return array;
    }
    public int[] getTime() {
        int[] array = {timeStep, noTimesteps, rainStep, rainData.length};
        return array;
    }
    public Date getStartDate() {
        Date sDate = startDate.getTime();
        return sDate;
    }
    public double[][] getSetup() {
        double[][] array = new double[noCells+2][4];
        array[0][0]=modelX[0];
        for(int i=1;i<noCells+2;i++) array[i][0] = array[i-1][0]+modelDX[i-1];
        for(int i=0;i<noCells+1;i++) array[i][3] = modelX[i];
        for(int i=0;i<noCells+2;i++){
            array[i][1]=modelSurf[i];
            array[i][2]=modelImp[i];
        }   
        return array;
    }
    public double[] getTimeStepWL(int outTS) {
        double[] array = new double[noCells+2];
        for(int i=0;i<noCells+2;i++) array[i]=modelWL[outTS][i];
        return array;
    }
    public double[] getMaxWL() {
        return maxWL;
    }
    public double[] getTimeStepResult(int outTS) {
        if(outTS == 0) outTS = 1;
        double[] array = new double[5];
        array[0]=rainModel[outTS-1];
        array[1]=TsR[outTS-1];
        array[2]=TsSD[outTS-1];
        array[3]=TsFL[outTS-1];
        array[4]=TsFR[outTS-1];
        
        return array;
    }
    public float[] getResult() {
        double totalRain = 0;
        double totalIL = 0;
        double totalCatch = 0;
        double totalRunoff = 0;
        double totalSurfDrain = 0;
        double totalFL = 0;
        double totalFR = 0;
        double minWaterDepth = 100;
        double changeStorage = 0;
        
        for(int c = 0; c<noCells; c++){
            totalCatch = totalCatch + cellCatch[c];
            changeStorage = changeStorage + (modelWL[noTimesteps][c+1] - modelWL[0][c+1])*cellWidth[c]*cellSY[c]*1000;
        }
        for(int i = 0; i<noTimesteps; i++){
            totalRain = totalRain + rainModel[i] * totalCatch;
            totalRunoff = totalRunoff + TsR[i];
            totalIL = totalIL + TsIL[i];
            totalSurfDrain = totalSurfDrain + TsSD[i];
            totalFL = totalFL + TsFL[i];
            totalFR = totalFR + TsFR[i];
        }
        for(int p =0; p<noCells; p++){
            if(minWaterDepth > modelSurf[p] - maxWL[p]) minWaterDepth = modelSurf[p] - maxWL[p];
        }
        float[] results = {(float)minWaterDepth, (float)changeStorage, 
            (float)totalRain, (float)totalIL, (float)totalRunoff, (float)totalFL, (float)totalFR, (float)totalSurfDrain};
        return results;
    }
    
    // internal functions
    private void printArray(double[] array){
        printArray(array, "float", 0, array.length-1);
    }
    private void printArray(double[] array, String outFormat){
        printArray(array, outFormat, 0, array.length-1);
    }
    private void printArray(double[] array, String outFormat, int iStart){
        printArray(array, outFormat, iStart, array.length-1);
    }
    private void printArray(double[] array, String outFormat, int iStart, int iEnd){
        if(outFormat == "float") {
            for(int c = iStart; c<iEnd; c++){
                outFile.print((float)array[c] + ", ");
            }
            outFile.print((float)array[iEnd]);
            outFile.print("\n");
        } else if(outFormat == "int") {
            for(int c = iStart; c<iEnd; c++){
                outFile.print((int)array[c] + ", ");
            }
            outFile.print((int)array[iEnd]);
            outFile.print("\n");
        } else if(outFormat == "date"){
            for(int c = iStart; c<iEnd; c++){
                outFile.println(dateFormat.format(array[c]) + ", ");
            }
            outFile.println(dateFormat.format(array[iEnd]));
            outFile.print("\n");
        } else {
            for(int c = iStart; c<iEnd; c++){
                outFile.print(String.format(outFormat,array[c]) + ", ");
            }
            outFile.print(String.format(outFormat,array[iEnd]));
            outFile.print("\n");    
        }
    }
    private void printArray2d(double[][] array, int constDim, int constIndex){
        printArray2d(array, "float", constDim, constIndex, 0, 0);
    }
    private void printArray2d(double[][] array, String outFormat, int constDim, int constIndex){
        printArray2d(array, outFormat, constDim, constIndex, 0, 0);
    }
    private void printArray2d(double[][] array, String outFormat, int constDim, int constIndex, int iStart){
        printArray2d(array, outFormat, constDim, constIndex, iStart, 0);
    }
    private void printArray2d(double[][] array, String outFormat, int constDim, int constIndex, int iStart, int iEnd){
        int lengthArray;
            
        if(constDim == 1){
            lengthArray = array[constIndex].length;
        } else {
            if(constDim == 2) {
                lengthArray = array.length;
            } else {
                outFile.print("Coding Error: error printing 2d array (dimention index) \n");
                return;
            }
        }
        
        if(iEnd == 0) iEnd = lengthArray - 1;

        if(outFormat == "float") {
            if(constDim == 1){
                for(int c = iStart; c<iEnd; c++){
                    outFile.print((float)array[constIndex][c] + ", ");
                }
                outFile.print((float)array[constIndex][iEnd] + "\n");
            } else {
                for(int c = iStart; c<iEnd; c++){
                    outFile.print((float)array[c][constIndex] + ", ");
                }
                outFile.print((float)array[iEnd][constIndex] + "\n");
            }
        } else if(outFormat == "int") {
            if(constDim == 1){
                for(int c = iStart; c<iEnd; c++){
                    outFile.print((int)array[constIndex][c] + ", ");
                }
                outFile.print((int)array[constIndex][iEnd] + "\n");
            } else {
                for(int c = iStart; c<iEnd; c++){
                    outFile.print((int)array[c][constIndex] + ", ");
                }
                outFile.print((int)array[iEnd][constIndex] + "\n");
            }
        } else if(outFormat == "date"){
            if(constDim == 1){
                for(int c = iStart; c<iEnd; c++){
                    outFile.print(dateFormat.format(array[constIndex][c]) + ", ");
                }
                outFile.print(dateFormat.format(array[constIndex][iEnd]) + "\n");
            } else {
                for(int c = iStart; c<iEnd; c++){
                    outFile.print(dateFormat.format(array[c][constIndex]) + ", ");
                }
                outFile.print(dateFormat.format(array[iEnd][constIndex]) + "\n");
            }
        } else {
            if(constDim == 1){
                for(int c = iStart; c<iEnd; c++){
                    outFile.print(String.format(outFormat,array[constIndex][c]) + ", ");
                }
                outFile.print(String.format(outFormat,array[constIndex][iEnd]) + "\n");
            } else {
                for(int c = iStart; c<iEnd; c++){
                    outFile.print(String.format(outFormat,array[c][constIndex]) + ", ");
                }
                outFile.print(String.format(outFormat,array[iEnd][constIndex]) + "\n");
            }    
        }
    }
    private static double doubleArrayMath(String out, double[] array, int index1, int index2) {
            double outputvalue = 0.0;
            
            if(index2 == 0) {
                index2 = Array.getLength(array) - 1;
            }
            // valid values of out are; "max", "min", "average"
            
            if("max".equals(out)) {
                for(int i=index1; i<=index2; i++){
                    if(array[i] > outputvalue){
                        outputvalue = array[i];
                    }
                }
            }
            if("min".equals(out)) {
                outputvalue = 999999;
                for(int i=index1; i<=index2; i++){
                    if(array[i] < outputvalue){
                        outputvalue = array[i];
                    }
                }
            }
            return outputvalue;
        }  
    private void setNoCells(int value){
        noCells = value;
        if(monitorLoad) {outFile.println("Found " + noCells + " cells");}
    }
    private double[] setArrayValue(double[] array, int value){
        for(int y = 0; y<array.length; y++){
            array[y] = value;
        }
        return array;
    }
}
