package com.manofj.minecraft.moj_dresolver.gson;


public class Checksum {

    private String md5;
    private String sha1;


    public String getMd5() {
        return md5;
    }

    public String getSha1() {
        return sha1;
    }


    public void setMd5( String md5 ) {
        this.md5 = md5;
    }

    public void setSha1( String sha1 ) {
        this.sha1 = sha1;
    }


    @Override
    public int hashCode() {
        int result = 1;
        result = result * 31 + ( md5 != null ? md5.hashCode() : 0 );
        result = result * 31 + ( sha1 != null ? sha1.hashCode() : 0 );
        return result;
    }

    @Override
    public boolean equals( Object obj ) {
        if ( obj == this ) return true;
        if ( obj == null || this.getClass() != obj.getClass() ) return false;

        Checksum other = ( Checksum )obj;
        return this.md5 == null ? other.md5 == null : this.md5.equals( other.md5 )
            && this.sha1 == null ? other.sha1 == null : this.sha1.equals( other.sha1 );
    }
}
