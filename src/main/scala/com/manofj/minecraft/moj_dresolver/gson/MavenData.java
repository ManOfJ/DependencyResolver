package com.manofj.minecraft.moj_dresolver.gson;


public class MavenData {

    private String url;
    private String groupId;
    private String artifactId;
    private String version;
    private Checksum checksum;


    public String getUrl() {
        return url;
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public Checksum getChecksum() {
        return checksum;
    }


    public void setUrl(String url) {
        this.url = url;
    }

    public void setGroupId(String groupId ) {
        this.groupId = groupId;
    }

    public void setArtifactId( String artifactId ) {
        this.artifactId = artifactId;
    }

    public void setVersion( String version ) {
        this.version = version;
    }

    public void setChecksum( Checksum checksum ) {
        this.checksum = checksum;
    }


    @Override
    public int hashCode() {
        int result = 1;
        result = result * 31 + ( url != null ? url.hashCode() : 0 );
        result = result * 31 + ( groupId != null ? groupId.hashCode() : 0 );
        result = result * 31 + ( artifactId != null ? artifactId.hashCode() : 0 );
        result = result * 31 + ( version != null ? version.hashCode() : 0 );
        result = result * 31 + ( checksum != null ? checksum.hashCode() : 0 );
        return result;
    }

    @Override
    public boolean equals( Object obj ) {
        if ( obj == this ) return true;
        if ( obj == null || this.getClass() != obj.getClass() ) return false;

        MavenData other = ( MavenData )obj;
        return this.url == null ? other.url == null : this.url.equals( other.url )
            && this.groupId == null ? other.groupId == null : this.groupId.equals( other.groupId )
            && this.artifactId == null ? other.artifactId == null : this.artifactId.equals( other.artifactId )
            && this.version == null ? other.version == null : this.version.equals( other.version )
            && this.checksum == null ? other.checksum == null : this.checksum.equals( other.checksum );
    }
}
