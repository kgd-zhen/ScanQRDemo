package com.zxing.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.regex.Pattern;

public class CommonUtils {

    private final static Pattern URL = Pattern
            .compile("^(https|http)://.*?$(net|com|.com.cn|org|me|)");
	
	/** 读满整个数组 */
	public static final void readBytes(InputStream in, byte[] buffer)
			throws IOException {
		readBytes(in, buffer, 0, buffer.length);
	}

	/** 读满指定长度的字节 */
	public static final void readBytes(InputStream in, byte[] buffer,
			int offset, int length) throws IOException {
		int sum = 0, readed;
		while (sum < length) {
			readed = in.read(buffer, offset + sum, length - sum);
			if (readed < 0) {
				throw new IOException("End of stream");
			}
			sum += readed;
		}
	}
	
    /**
     * 判断是否为一个合法的url地址
     * 
     * @param str
     * @return
     */
    public static boolean isUrl(String str) {
        if (str == null || str.trim().length() == 0)
            return false;
        return URL.matcher(str).matches();
    }
}
