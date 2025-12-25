package com.hepsiburada;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.openqa.selenium.By;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class LocatorHelper {

    private final Map<String, LocatorDef> elements;

    public LocatorHelper(String elementsJsonPath) {
        this.elements = loadElements(elementsJsonPath);
    }

    public By getBy(String elementKey) {
        LocatorDef def = elements.get(elementKey);
        if (def == null) {
            throw new RuntimeException("elements.json içinde key bulunamadı: " + elementKey);
        }

        String type = def.type.toLowerCase(Locale.ROOT);

        switch (type) {
            case "css":
            case "cssselector":
                return By.cssSelector(def.value);
            case "xpath":
                return By.xpath(def.value);
            case "id":
                return By.id(def.value);
            case "name":
                return By.name(def.value);
            case "classname":
                return By.className(def.value);
            case "tagname":
                return By.tagName(def.value);
            case "linktext":
                return By.linkText(def.value);
            case "partiallinktext":
                return By.partialLinkText(def.value);
            default:
                throw new RuntimeException("Desteklenmeyen locator type: " + def.type);
        }
    }

    private Map<String, LocatorDef> loadElements(String path) {
        try {
            ObjectMapper om = new ObjectMapper();
            LocatorDef[] arr = om.readValue(new File(path), LocatorDef[].class);

            Map<String, LocatorDef> map = new HashMap<>();
            for (LocatorDef d : arr) {
                map.put(d.key, d);
            }
            return map;

        } catch (Exception e) {
            throw new RuntimeException("elements.json okunamadı: " + path, e);
        }
    }

    public static class LocatorDef {
        public String key;
        public String type;
        public String value;
    }
}
