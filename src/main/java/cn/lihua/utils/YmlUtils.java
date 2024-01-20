package cn.lihua.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

/**
 * 读取yml配置的工具类
 * @author liujh
 */
public class YmlUtils {
	
	public static final String defaultFileName = "application.yml";
	
	/**
     * key:文件名索引
     * value:配置文件内容
     */
	@SuppressWarnings("rawtypes")
	private static Map<String, LinkedHashMap> ymls = new HashMap<>();
    
    /**
     * 加载配置文件
     * @param fileName
     */
    private static void loadYml(String fileName) {
        if (!ymls.containsKey(fileName)) {
        	
        	InputStream in = null;
        	try{
        		in = YmlUtils.class.getResourceAsStream("/" + fileName);
                ymls.put(fileName, new Yaml().loadAs(in, LinkedHashMap.class));
        	}catch (Exception e) {
				throw new RuntimeException("解析yml文件失败,文件不存在或yml文件格式有误.");
			} finally {
				if (in != null) {
					try {
						in.close();
					} catch (IOException e) {
						throw new RuntimeException("关闭文件流失败.");
					}
				}
			}
        	
        }
    }
    
    /**
     * 重新加载
     * @param fileName
     */
    public static void reloadYml(String fileName){
    	InputStream in = null;
    	try{
    		in = YmlUtils.class.getResourceAsStream("/" + fileName);
            ymls.put(fileName, new Yaml().loadAs(in, LinkedHashMap.class));
    	}catch (Exception e) {
			throw new RuntimeException("解析yml文件失败,文件不存在或yml文件格式有误.");
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
					throw new RuntimeException("关闭文件流失败.");
				}
			}
		}
    }
    
    /**
     * 清除fileName中加载的数据
     * @param fileName
     * @param key
     * @return
     */
    public static void removeYml(String fileName) {
        if (ymls.containsKey(fileName)) {
        	ymls.remove(fileName);
        }
    }
    
    /**
     * 写回去yml
     * @param ymlsMap
     * @throws IOException 
     */
    public static void writeToYml(Map<String, Object> ymlsMap,File distFile) throws IOException{
    	Yaml yaml = new Yaml();
    	DumperOptions dumperOptions = new DumperOptions();
    	dumperOptions.setPrettyFlow(true);
    	yaml.dump(ymlsMap, new FileWriter(distFile));
    }
    
    /**
     * 获取配置文件的所有值
     * @param fileName
     * @return
     */
	@SuppressWarnings("rawtypes")
	public static LinkedHashMap getValues(String fileName) {
		
		// 首先加载配置文件
        loadYml(fileName);
        return ymls.get(fileName);
		
    }
    
    //值不存在的话返回null
    @SuppressWarnings("rawtypes")
	public static Object getValue(String fileName, String key) {
        
    	// 首先加载配置文件
        loadYml(fileName);
        
        // 首先将key进行拆分
        String[] keys = key.split("[.]");

        // 将配置文件进行复制
		Map ymlInfo = (Map) ymls.get(fileName).clone();
        for (int i = 0; i < keys.length; i++) {
            Object value = ymlInfo.get(keys[i]);
            if(value == null) return null;
            
            if (i < keys.length - 1) {
                ymlInfo = (Map) value;
            }else {
                return value;
            }
            
        }
        
        return null;
        
    }
    
	public static Object getValue(String fileName, String key, Object defaultValue) {
    	Object result = getValue(fileName, key);
    	return result == null ? defaultValue : result;
    }
    
    public static Object getValueFromDefaultFile(String key) {
    	return getValue(defaultFileName,key);
    }
    
    public static Object getValueFromDefaultFile(String key,Object defaultValue) {
    	Object result = getValue(defaultFileName,key);
    	return result == null ? defaultValue : result;
    }

}
