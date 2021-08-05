package ee.sammee;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.*;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

/**
 *
 * @author K.Norris - mappazzo.com
 */

public class SammeeLicense {

    PBEParameterSpec pbeParamSpec;
    SecretKey pbeKey;
    private String sessionUser;
    private Calendar sessionTime;
    private String sessionTimeString;
    private Path licensePath;
    private String licensePathString;
    private SimpleDateFormat dateStore = new SimpleDateFormat("yyyy.MM.dd");
    
    // Server setup
    private String badEmailsURL = "http://www.urbaqua.org.au/wp-content/themes/urbaqua/sammee/badEmails.txt";
    private String controlURL = "http://www.urbaqua.org.au/wp-content/themes/urbaqua/sammee/control.txt";
    private String licenseDbURL = "http://www.urbaqua.org.au/wp-content/themes/urbaqua/sammee/license.php";
    private String adminEmail = "kelly@mappazzo.com";
    
    // Fields for License Data
    private int licenseNumber = -1;
    private String licenseType = "NONE";
    private int licenseVersion;
    private String licenseExpireString;
    private Calendar licenseExpire;
    private String licenseEmail;
    private String licenseUser;
    private int licenseRunCount;
    private int licenseRunsSinceCheck;
    
    // License Setup Parameters
    private String licenseHeader = "SammEE";
    private int sessionVersion = 1603;
    private boolean updateOldLicenses = true; 
    private String simplePbePass = "mespessp3w"; 
    private int licRunsToCheck = 10;
    private int licRunsToFail = 15;
    private boolean emailReports = false;
    private String licenseFile = "sammee.lic";
    private String[] invalidEmail = {"",
        "JoBloggs@gmail.com",
        "JaneDo@gmail.com",
    };
    
    // Initialisation
    public SammeeLicense() {
        char[] passChar = simplePbePass.toCharArray();
        createKey(passChar);
        getSessionData();
    }
    private void getSessionData() {
        sessionUser = System.getProperty("user.name").toString();     
        sessionTime = Calendar.getInstance();
        sessionTimeString = dateStore.format(sessionTime.getTime());
        licensePathString = System.getProperty("user.home").toString() + 
                System.getProperty("file.separator").toString() + licenseFile;
        licensePath = Paths.get(licensePathString);
    }
    
    // Methods
    public String checkControl() {
        try {
            URL verURL = new URL(controlURL);
            String Line = "blank";
            String[] LineArray;
            int minVersion = -1;
            int currentVersion = -1;
            
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(verURL.openStream()));
            while((Line = in.readLine()) != null){
                LineArray = Line.split(",");
                // System.out.println(Line);
                
                if("minVer".equals(LineArray[0])) minVersion = Integer.parseInt(LineArray[1]);
                if("currentVer".equals(LineArray[0])) currentVersion = Integer.parseInt(LineArray[1]);
                if("emailReports".equals(LineArray[0])) emailReports = Boolean.parseBoolean(LineArray[1]);
                if("runsToCheck".equals(LineArray[0])) {
                    licRunsToCheck = Integer.parseInt(LineArray[1]);
                    licRunsToFail = licRunsToCheck + 5;
                }
            }
            //System.out.println("current:" + currentVersion);
            
            if(minVersion == -1) {
                licenseRunsSinceCheck = licenseRunsSinceCheck + 1;
                return "connection error";
            }
            if(minVersion <= sessionVersion) { 
                if(currentVersion > sessionVersion) return "update";
                return "current";
            }
            return "old";
        } catch (IOException ex) {
            licenseRunsSinceCheck = licenseRunsSinceCheck + 1;
            return "connection error";
        }
    }
    public boolean checkBadEmail(String InputEmail) {
        try {
            URL checkURL = new URL(badEmailsURL);
            String Line = "blank";
            String[] LineArray;
            
            BufferedReader in = new BufferedReader(new InputStreamReader(checkURL.openStream()));
            while((Line = in.readLine()) != null){
                LineArray = Line.split(",");
                //System.out.println(Line);
                for(int i=0; i < LineArray.length; i++) {
                    if(InputEmail.equals(LineArray[i])) return true;
                }
            }
        } catch (IOException ex) {
            System.out.println("Failed to check online bad email list");
        }
        for(int i=0; i < invalidEmail.length; i++) {
            if(InputEmail.equals(invalidEmail[i])) return true;
        }
        return false;
    }
    public boolean sendAdminReport(String subject, String report) {
        String host = "smtp.gmail.com";
        int port = 465;
        final String from = "lic.sammee@gmail.com";
        //String to = "info@essentialenvironmental.com.au";
        String to = adminEmail;
        String SSL_FACTORY = "javax.net.ssl.SSLSocketFactory";
        Security.addProvider(new com.sun.net.ssl.internal.ssl.Provider());
            
        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.port", port);
            props.put("mail.smtp.socketFactory.port", port);
            props.put("mail.smtp.socketFactory.class", SSL_FACTORY);
            props.put("mail.smtp.socketFactory.fallback", "false");

            Session session = Session.getDefaultInstance(props,
                    new javax.mail.Authenticator() {
                        protected PasswordAuthentication getPasswordAuthentication() {
                            return new PasswordAuthentication(from, "**password**");
                        }
                    });
        
            Message message = new MimeMessage(session);
            InternetAddress addressFrom = new InternetAddress(from);
            message.setFrom(addressFrom);
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));

            // Setup a MimeMultipart message to handle file attachments
            Multipart multipart = new MimeMultipart();
            BodyPart messageBodyPart = new MimeBodyPart();
            messageBodyPart.setContent(report, "text/html");
            multipart.addBodyPart(messageBodyPart);
            // addAttachments(attachments, multipart);

            // Set up message
            message.setSubject(subject);
            message.setContent(multipart);
            
            Transport.send(message);
            
        } catch(AuthenticationFailedException e) {
            System.out.println("Authentication Failed Exception");
            return false;
        } catch (MessagingException mex) {
            System.out.println("Send failed, exception: " + mex);
            return false;
        } 
        return true;
    }
    public String checkLicense() {
        String outcome = "Good";
        String adminSubject;
        String adminReport;
        
        try {
        // Get information from license file
            byte[] LicenseBytes = readByteFile(licensePath);
            String LicenseString = decrypt(LicenseBytes);
            String[] LineArray = LicenseString.split(",");
            System.out.println("License: " + LicenseString);
            
            if(!LineArray[0].equals(licenseHeader)) {
                outcome = "Incorrect File";
                return outcome;
            } else {
                // Get Data from License File
                licenseNumber = Integer.parseInt(LineArray[1]);
                licenseType = LineArray[2];
                licenseVersion = Integer.parseInt(LineArray[3]);
                licenseExpireString = LineArray[4];
                licenseExpire = Calendar.getInstance();
                licenseExpire.setTime(dateStore.parse(licenseExpireString));
                licenseEmail = LineArray[5];
                licenseUser = LineArray[6];
                licenseRunCount = Integer.parseInt(LineArray[7]) + 1;
                licenseRunsSinceCheck = Integer.parseInt(LineArray[8]) + 1;
                
                if(licenseVersion < sessionVersion) {
                    if(updateOldLicenses) { 
                        licenseVersion = sessionVersion;
                        outcome = "new version";
                    } else {
                        outcome = "Bad Version";
                    }
                }
            }
            
            if(licenseRunsSinceCheck >= licRunsToCheck) outcome = checkLicenseDB();
            
            // Check for inconsistencies
            if(sessionTime.after(licenseExpire)) outcome = "License Expired";
            if(checkBadEmail(licenseEmail)) outcome = "Bad Email";
            if(!licenseUser.equals(sessionUser)) outcome = "Bad User";
            if(licenseRunsSinceCheck > licRunsToFail && "Connection fail".equals(outcome)) outcome = "Max Fails";
            
            if(!outcome.equals("Good") && emailReports) {
                adminSubject = "License error";
                adminReport = outcome + ", " + sessionVersion + ", user: " + sessionUser;
                sendAdminReport(adminSubject, adminReport);
            }
            writeLicenseFile();
        } catch (IOException ex) {
            outcome = "Missing File";
        } catch (ParseException | IllegalBlockSizeException | BadPaddingException ex) {
            outcome = "Corrupt File";
            if(emailReports) {
                   adminSubject = "Old or corrupt License Found";
                   adminReport = licensePathString + ", User:" + sessionUser;
                   sendAdminReport(adminSubject, adminReport);
            }
        }
        return outcome;
    }
    public String checkLicenseDB() {
        String outcome = "Good";
        String adminSubject;
        String adminReport;
        String[] dbLicData = dbQuery(licenseNumber, "getLic", null);

        if("SammEE License Server".equals(dbLicData[0])) {
            if(!licenseType.equals(dbLicData[2])) {
                licenseType = dbLicData[2];
                outcome = "upgrade";
            }
            if(licenseVersion > Integer.parseInt(dbLicData[3])) {
                dbQuery(licenseNumber, "setVer", Integer.toString(licenseVersion));
            }
            if(!licenseUser.equals(dbLicData[5])) {
                licenseUser = dbLicData[5];
            }
            if(!licenseExpireString.equals(dbLicData[6])) {
                licenseExpireString = dbLicData[6];
                try {
                    licenseExpire.setTime(dateStore.parse(licenseExpireString));
                    outcome = "expire changed";
                } catch (ParseException ex) { 
                    if(emailReports) {
                        adminSubject = "SammEE Error";
                        adminReport = "Error in database, incorrect expiry date format, Lic Number: " + licenseNumber;
                        sendAdminReport(adminSubject, adminReport);
                    }
                    outcome = "software error";
                }
            }
            if(licenseRunCount != Integer.parseInt(dbLicData[7])) {
                int newRunCount;
                if(licenseRunCount > Integer.parseInt(dbLicData[7])) {
                    newRunCount = licenseRunCount; 
                } else {
                    newRunCount = Integer.parseInt(dbLicData[7]) + licenseRunCount;
                }
                String[] setRunsResult = dbQuery(licenseNumber, "setRuns", Integer.toString(newRunCount));
                if("badRuns".equals(setRunsResult[1])) {
                    adminSubject = "SammEE Error on setRuns";
                    adminReport = "Lic Number: " + licenseNumber + " badRuns";
                    sendAdminReport(adminSubject, adminReport);
                }
            }
            licenseRunsSinceCheck = 0;
        } else {
            licenseRunsSinceCheck = licenseRunsSinceCheck + 1;
            outcome = dbLicData[0];
        }
        return outcome;
    }
    public String getExpiry() {
        return licenseExpireString;
    }
    public int getVersion() {
        return sessionVersion;
    }
    public int getNumber() {
        return licenseNumber;
    }
    public String getLicType() {
        return licenseType;
    }
    public String writeLicenseFile() {
        String adminSubject;
        String adminReport;
        
        String NewLicenseString = licenseHeader;
        NewLicenseString = NewLicenseString + "," + licenseNumber;
        NewLicenseString = NewLicenseString + "," + licenseType;
        NewLicenseString = NewLicenseString + "," + licenseVersion;
        NewLicenseString = NewLicenseString + "," + licenseExpireString;
        NewLicenseString = NewLicenseString + "," + licenseEmail;
        NewLicenseString = NewLicenseString + "," + licenseUser;
        NewLicenseString = NewLicenseString + "," + licenseRunCount;
        NewLicenseString = NewLicenseString + "," + licenseRunsSinceCheck;
               //  System.out.println(NewLicenseString);

        try {
            byte[] licenseBytes = encrypt(NewLicenseString);
            writeByteFile(licenseBytes, licensePath);
            return "good";
        } catch (IOException ex) {
            if(emailReports) {
                adminSubject = "Could not write license file";
                adminReport = licensePathString + " \n for " 
                        + licenseEmail + "\n" + NewLicenseString;
                sendAdminReport(adminSubject, adminReport);
            }
            return "bad access";
        }
    }
    public String createLicense(String InputEmail, int InputLicNum) {
        String outcome = "software error";
        String[] dbLicData = dbQuery(InputLicNum, "getLic");
        
        //System.out.println("Data[0]: " + dbLicData[0]);
        //System.out.println("Data[4], email: " + dbLicData[4]);
        
        if("SammEE License Server".equals(dbLicData[0]) && dbLicData[4].equals(InputEmail)) {
            if(sessionUser.equals(dbLicData[5]) || "".equals(dbLicData[5])) {
                if("".equals(dbLicData[5])) dbQuery(InputLicNum, "setUser", sessionUser);
                dbQuery(InputLicNum, "setVer", Integer.toString(sessionVersion));
                dbQuery(InputLicNum, "addInst");
                licenseRunCount = Integer.parseInt(dbLicData[7]);
                licenseExpireString = dbLicData[6];
                //System.out.println("licenseExpireString: " + dbLicData[6]);
                try {
                    licenseExpire = Calendar.getInstance();
                    licenseExpire.setTime(dateStore.parse(licenseExpireString));
                } catch (ParseException ex) { 
                    if(emailReports) sendAdminReport("SammEE Error", "Error in database, incorrect expiry date format, Lic Number: " + licenseNumber);
                    outcome = "software error";
                }
                licenseType = dbLicData[2];
                licenseVersion = sessionVersion;
                licenseUser = sessionUser;
                licenseEmail = InputEmail;
                licenseNumber = Integer.parseInt(dbLicData[1]);
                licenseRunsSinceCheck = 0;
                
                String adminSubject = "New User License Created";
                sendAdminReport(adminSubject, licenseType + ", " + licenseNumber + ", " + licenseEmail);
                outcome = writeLicenseFile();
            } else {
                outcome = "bad user";
            }
        } else {
            if("SammEE License Server".equals(dbLicData[0])){
                outcome = "bad email";
            } else {
                outcome = dbLicData[0];
            }  
        }
        return outcome;
    }
    public String newLicense(String InputEmail, int InputLicNum) {
        String outcome = "software error";
        String[] dbLicData = dbQuery(InputLicNum, "getLic");
        
        //System.out.println("Data[0]: " + dbLicData[0]);
        //System.out.println("Data[4], email: " + dbLicData[4]);
        
        if("SammEE License Server".equals(dbLicData[0]) && dbLicData[4].equals(InputEmail)) {
            if(sessionUser.equals(dbLicData[5]) || "".equals(dbLicData[5])) {
                if("".equals(dbLicData[5])) dbQuery(InputLicNum, "setUser", sessionUser);
                dbQuery(InputLicNum, "setVer", Integer.toString(sessionVersion));
                dbQuery(InputLicNum, "addInst");
                licenseRunCount = Integer.parseInt(dbLicData[7]);
                licenseExpireString = dbLicData[6];
                //System.out.println("licenseExpireString: " + dbLicData[6]);
                try {
                    licenseExpire = Calendar.getInstance();
                    licenseExpire.setTime(dateStore.parse(licenseExpireString));
                } catch (ParseException ex) { 
                    if(emailReports) sendAdminReport("SammEE Error", "Error in database, incorrect expiry date format, Lic Number: " + licenseNumber);
                    outcome = "software error";
                }
                licenseType = dbLicData[2];
                licenseVersion = sessionVersion;
                licenseUser = sessionUser;
                licenseEmail = InputEmail;
                licenseNumber = Integer.parseInt(dbLicData[1]);
                licenseRunsSinceCheck = 0;
                
                String adminSubject = "New User License Created";
                sendAdminReport(adminSubject, licenseType + ", " + licenseNumber + ", " + licenseEmail);
                outcome = writeLicenseFile();
            } else {
                outcome = "bad user";
            }
        } else {
            if("SammEE License Server".equals(dbLicData[0])){
                outcome = "bad email";
            } else {
                outcome = dbLicData[0];
            }  
        }
        return outcome;
    }
    private String[] dbQuery(int dbLicense, String dbAction) {
        return dbQuery(dbLicense, dbAction, "0");
    }
    private String[] dbQuery(int dbLicense, String dbAction, String setValue) {
        String outcome[] = {"error","error"};
        try {
            // open a connection to the site
            URL dbQueryURL = new URL(licenseDbURL);
            URLConnection dbCon = dbQueryURL.openConnection();
            // activate the output
            dbCon.setDoOutput(true);
            PrintStream ps = new PrintStream(dbCon.getOutputStream());
            ps.print("licNum="+dbLicense);
            ps.print("&action="+dbAction);
            ps.print("&setVal="+setValue);

            // opening the input stream sends the request
            BufferedReader In = new BufferedReader(new InputStreamReader(dbCon.getInputStream()));
            
            // URL dbQueryURL = new URL(licenseDbURL + "?setVal=" + setValue + "&action=" + dbAction + "&licNum=" + dbLicense);
            // BufferedReader In = new BufferedReader(new InputStreamReader(dbQueryURL.openStream()));
            
            String inLine = In.readLine();
            String[] inData = inLine.split(",");
            // System.out.println("licDbQuery: licNum="+dbLicense+"&action="+dbAction+"&setVal="+setValue);
            // System.out.println("licDbResult: " + inLine);
            
            ps.close();
            
            if(!"SammEE License Server".equals(inData[0])) {
                outcome[0] = "Connection fail";
            } else if("badLicense".equals(inData[1])) {
                outcome[0] = "Bad License";
            } else if ("dbError".equals(inData[1])){
                if(emailReports) sendAdminReport("SammEE Error", "SammEE dbError on " + dbAction + ", Lic Number: " + licenseNumber + " dbError: " + inData[2]);
                outcome[0] = "software error";
            } else {
                outcome = inData;
            }
        } catch (MalformedURLException ex) { 
            Logger.getLogger(SammeeLicense.class.getName()).log(Level.SEVERE, null, ex);
            outcome[0] = "Connection fail";
        } catch (IOException ex) {
            Logger.getLogger(SammeeLicense.class.getName()).log(Level.SEVERE, null, ex);
            outcome[0] = "Connection fail";
        }
        return outcome;
    }
    
    // Simple PBE
    private boolean createKey(char[] password) {
        byte[] salt = {(byte)0xc7, (byte)0x73, (byte)0x21, (byte)0x8c, 
            (byte)0x7e, (byte)0xc8, (byte)0xee, (byte)0x99 };
        int itt = 20;
        return createKey(password,salt,itt);
    }    
    private boolean createKey(char[] password, int itt) {
        byte[] salt = {(byte)0xc7, (byte)0x73, (byte)0x21, (byte)0x8c, 
            (byte)0x7e, (byte)0xc8, (byte)0xee, (byte)0x99 };
        return createKey(password,salt,itt);
    } 
    private boolean createKey(char[] password, byte[] salt) {
        int itt = 20;
        return createKey(password,salt,itt);
    }  
    private boolean createKey(char[] password, byte[] salt, int itt) {
        SecretKeyFactory keyFac;
        PBEKeySpec pbeKeySpec;
        
        // Create PBE parameter set
        pbeParamSpec = new PBEParameterSpec(salt, itt);
        pbeKeySpec = new PBEKeySpec(password);
            
        try {
            keyFac = SecretKeyFactory.getInstance("PBEWithMD5AndDES");
            pbeKey = keyFac.generateSecret(pbeKeySpec);
        } catch (InvalidKeySpecException | NoSuchAlgorithmException ex) {
            Logger.getLogger(SammeeLicense.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        
        return true;
    }
    public byte[] encrypt(String input) {    
        String txtFormat = "UTF-8";
        return encrypt(input, txtFormat);
    }
    public byte[] encrypt(String input, String txtFormat) {    
	byte[] inBytes = null;
        byte[] encryptBytes = null;
    
        // convert input string to bytes
        try {
            inBytes = input.getBytes(txtFormat);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(SammeeLicense.class.getName()).log(Level.SEVERE, null, ex);
        }
        // Apply PBE Cipher
        try {
            Cipher pbeCipherEC = Cipher.getInstance("PBEWithMD5AndDES");
            pbeCipherEC.init(Cipher.ENCRYPT_MODE, pbeKey, pbeParamSpec);       
            encryptBytes = pbeCipherEC.doFinal(inBytes);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException | 
                NoSuchAlgorithmException | NoSuchPaddingException | 
                BadPaddingException | IllegalBlockSizeException ex) {
            Logger.getLogger(SammeeLicense.class.getName()).log(Level.SEVERE, null, ex);
        }     
        return encryptBytes;
    }
    public String decrypt(byte[] input) throws IllegalBlockSizeException, BadPaddingException {    
        String txtFormat = "UTF-8";
        return decrypt(input, txtFormat);
    } 
    public String decrypt(byte[] input, String txtFormat) throws IllegalBlockSizeException, BadPaddingException {
        byte[] decryptedBytes = null;
        String decryptedString = null;
        
        // Apply PBE Cipher
        try {
            Cipher pbeCipherDC = Cipher.getInstance("PBEWithMD5AndDES");
            pbeCipherDC.init(Cipher.DECRYPT_MODE, pbeKey, pbeParamSpec);
            decryptedBytes = pbeCipherDC.doFinal(input);
        } catch (InvalidKeyException | InvalidAlgorithmParameterException | 
                NoSuchAlgorithmException | NoSuchPaddingException ex) {
            Logger.getLogger(SammeeLicense.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        // Decode bytes to string
        try {
            decryptedString = new String(decryptedBytes, txtFormat);
        } catch (UnsupportedEncodingException ex) {
            Logger.getLogger(SammeeLicense.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        return decryptedString;
    }
    public boolean writeByteFile(byte[] data, Path outputFile) throws FileNotFoundException, IOException { 
        String outputString = outputFile.toString();
        try {
            FileOutputStream out = new FileOutputStream(outputString);
            out.write(data);
            out.close();
        } catch (IOException ex) {
            return false;
        }
        return true;
    }
    private byte[] readByteFile(Path inputFile) throws FileNotFoundException, IOException {  
        File readFile = new File(inputFile.toString());
        int length = (int) readFile.length();  
        byte[] array = new byte[length];  
        FileInputStream in = new FileInputStream(readFile);  
        int offset = 0;  
        while (offset < length) {  
            int count = in.read(array, offset, (length - offset));  
            offset += count;  
        }  
        in.close();  
        return array;  
    }
}    