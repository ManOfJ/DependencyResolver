package com.manofj.minecraft.moj_dresolver.gson;


public class LibraryData {

    private MavenData maven;
    private Boolean serverreq;
    private Boolean clientreq;


    public MavenData getMaven() {
        return maven;
    }

    public Boolean getServerreq() {
        return serverreq;
    }

    public Boolean getClientreq() {
        return clientreq;
    }


    public void setMaven( MavenData maven ) {
        this.maven = maven;
    }

    public void setServerreq( Boolean serverreq ) {
        this.serverreq = serverreq;
    }

    public void setClientreq( Boolean clientreq ) {
        this.clientreq = clientreq;
    }


    @Override
    public int hashCode() {
        int result = 1;
        result = result * 31 + ( maven != null ? maven.hashCode() : 0 );
        result = result * 31 + ( serverreq != null ? serverreq.hashCode() : 0 );
        result = result * 31 + ( clientreq != null ? clientreq.hashCode() : 0 );
        return result;
    }

    @Override
    public boolean equals( Object obj ) {
        if ( obj == this ) return true;
        if ( obj == null || this.getClass() != obj.getClass() ) return false;

        LibraryData other = ( LibraryData )obj;
        return this.maven == null ? other.maven == null : this.maven.equals( other.maven )
            && this.serverreq == null ? other.serverreq == null : this.serverreq.equals( other.serverreq )
            && this.clientreq == null ? other.clientreq == null : this.clientreq.equals( other.clientreq );
    }
}
