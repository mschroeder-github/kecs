package de.dfki.sds.mschroeder.commons.lang;

import java.util.Arrays;
import java.util.List;

/**
 *
 * 
 */
public class RegexUtility {
    
    public static String quote(String text) {
        //\.[]{}()*+-?^$|
        //<([{\^-=$!|]})?*+.>
        List<String> special = Arrays.asList(
                ".", "\\", "-", "{", "}", "[", "]", 
                "(", ")", "*", "+", "?", "^", "$", "|",
                "<", ">", "=", "!"
                );
        String escaper = "\\";
        StringBuilder sb = new StringBuilder();
        for(int i = 0; i < text.length(); i++) {
            String chara = String.valueOf(text.charAt(i));
            if(special.contains(chara)){
                sb.append(escaper);
            }
            sb.append(chara);
        }
        return sb.toString();
    }
    
}
