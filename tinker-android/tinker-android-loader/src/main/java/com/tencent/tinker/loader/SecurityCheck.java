package com.tencent.tinker.loader;
public class SecurityCheck {

    private String meta;
    public boolean securityCheck(ShareSecurityCheck securityCheck,String META_FILE)
    {
        meta = securityCheck.getMetaContentMap().get(META_FILE);
        //not found resource
        if (meta == null) {
            return true;
        }
        return false;
    }
    public String getMeta()
    {
        return meta;
    }

}
