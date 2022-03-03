package com.r6lab.sparkjava.jwt.user;

import com.r6lab.sparkjava.jwt.controller.AuthController;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.FileUtils;
import org.mindrot.jbcrypt.BCrypt;

public final class UserService {

    private Map<String, User> name2user;
    
    public UserService(File userCsvFile) {
        name2user = new HashMap<>();
        try {
            load(userCsvFile);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    private void load(File userCsvFile) throws IOException {
        if(!userCsvFile.exists()) {
            StringBuilder sb = new StringBuilder();
            sb.append("username,password,first name,last name\n");
            sb.append("test,test,Test,Test\n");
            FileUtils.writeStringToFile(userCsvFile, sb.toString(), StandardCharsets.UTF_8);
        }
        
        CSVParser p = CSVFormat.DEFAULT.withFirstRecordAsHeader().parse(new FileReader(userCsvFile));
        for(CSVRecord record : p.getRecords()) {
            String passwordHash = BCrypt.hashpw(record.get(1), AuthController.BCRYPT_SALT);
            register(record.get(0), record.get(1), passwordHash, record.get(2), record.get(3));
        }
        p.close();
    }
    
    public final void register(String userName, String password, String passwordHash, String firstName, String lastName) {
        System.out.println("[User] " + userName + " (" + firstName + " " + lastName + ")");
        
        if(firstName.trim().isEmpty()) {
            throw new IllegalArgumentException("First name is empty.");
        }
        if(lastName.trim().isEmpty()) {
            throw new IllegalArgumentException("Last name is empty.");
        }
        
        if(!userName.matches("\\w+")) {
            throw new IllegalArgumentException("Username has to match [a-zA-Z_0-9]+ (no spaces or symbols).");
        }
        
        if(password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password is empty.");
        }
        
        name2user.put(userName, User.of(userName, passwordHash, firstName, lastName));
    }

    public final User get(String userName) {
        return name2user.get(userName);
    }
    
}
