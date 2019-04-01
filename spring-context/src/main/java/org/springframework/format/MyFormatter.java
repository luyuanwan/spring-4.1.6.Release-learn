package org.springframework.format;

import java.text.ParseException;
import java.util.Locale;

/**
 * Created by xiang on 2018/2/22.
 */
public class MyFormatter implements Formatter<String> {

    @Override
    public String parse(String text, Locale locale) throws ParseException {
        return null;
    }

    @Override
    public String print(String object, Locale locale) {
        return null;
    }
}
