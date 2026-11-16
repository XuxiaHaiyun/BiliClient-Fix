package com.RobinNotBad.BiliClient.util;

import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

import javax.crypto.Cipher;

/**
 * B站登录用的 RSA 加密工具（PKCS#1 v1.5 + Base64）。
 * 公钥来自 https://passport.bilibili.com/x/passport-login/web/key 的 data.key（PEM 格式）。
 */
public class RsaUtil {

    /**
     * 使用 X509/PEM 公钥加密文本，返回 Base64 结果。
     *
     * @param publicKeyPem PEM 格式公钥（可带 -----BEGIN PUBLIC KEY----- 头尾）
     * @param text         待加密明文
     */
    public static String encryptByPublicKey(String publicKeyPem, String text) throws Exception {
        String normalized = publicKeyPem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("-----BEGIN RSA PUBLIC KEY-----", "")
                .replace("-----END RSA PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] keyBytes = Base64.decode(normalized, Base64.DEFAULT);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PublicKey publicKey = keyFactory.generatePublic(spec);

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
        return Base64.encodeToString(encrypted, Base64.NO_WRAP);
    }
}
