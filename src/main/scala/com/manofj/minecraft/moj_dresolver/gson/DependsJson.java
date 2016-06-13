package com.manofj.minecraft.moj_dresolver.gson;

import java.util.List;


public class DependsJson {

    private List< LibraryData > libraries;


    public List< LibraryData > getLibraries() {
        return libraries;
    }


    public void setLibraries( List< LibraryData > libraries ) {
        this.libraries = libraries;
    }


    @Override
    public int hashCode() {
        int result = 1;
        result = result * 31 + ( libraries != null ? libraries.hashCode() : 0 );
        return result;
    }

    @Override
    public boolean equals( Object obj ) {
        if ( obj == this ) return true;
        if ( obj == null || this.getClass() != obj.getClass() ) return false;

        DependsJson other = ( DependsJson )obj;
        return this.libraries == null ? other.libraries == null : this.libraries.equals( other.libraries );
    }
}
