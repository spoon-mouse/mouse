package com.mouse.backend.util;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public class KvStringSplit {

    public static Map<String, String> split(String data){

        Map<String, String> keyValueMap = Arrays.stream(data.split(" "))
                .map(kv -> kv.split("="))
                .filter(kvArray -> kvArray.length == 2)
                .collect(Collectors.toMap(kv -> kv[0], kv -> kv[1]));

        return keyValueMap;
    }

}
