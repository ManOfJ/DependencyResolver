package com.manofj.minecraft.moj_dresolver.launcher

import java.io.File
import java.net.URI
import java.util.jar.JarFile
import java.util.{ List => JavaList }

import scala.language.implicitConversions

import com.google.gson.Gson
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.{ Charsets, FileUtils, IOUtils }

import net.minecraft.launchwrapper.{ ITweaker, LaunchClassLoader }

import net.minecraftforge.fml.common.FMLLog
import net.minecraftforge.fml.relauncher.{ FMLInjectionData, FMLLaunchHandler }

import com.manofj.minecraft.moj_dresolver.gson.{ DependsJson, LibraryData, MavenData }


class DependencyResolver
  extends ITweaker
{
  // TODO: ソースコードの清書を行う
  // TODO: コメントを記述する
  // TODO: ClassLoaderExclusion を追加できるようにする
  // TODO: Bintray リポジトリに対応する
  // TODO: 前提 Mod も依存関係解決できるようにする


  private[ this ] final val OPTION_MINECRAFT_ROOT = "com.manofj.minecraft.moj_dresolver.launcher.DependencyResolver.mc-root"

  private[ this ] final val repositoryAlias = {
    val tmp = Map.newBuilder[ String, String ]
    tmp += "central" -> "http://central.maven.org/maven2/"
    tmp += "mavencentral" -> "http://central.maven.org/maven2/"
    tmp.result().withDefault( String.valueOf )
  }


  private[ this ] final lazy val minecraftDir =
    Option( System.getProperty( OPTION_MINECRAFT_ROOT ) )
      .orElse( Some( System.getenv( "APPDATA" ) + "/.minecraft" ) )
      .map( new File( _ ).getCanonicalFile )
      .filter( _.isDirectory )

  private[ this ] final lazy val libraryDir =
    minecraftDir
      .map( new File( _, "libraries" ) )
      .filter( _.isDirectory )

  private[ this ] final lazy val modsDir =
    Option( FMLInjectionData.data()( 6 ) )
      .collect { case gameDir: File => new File( gameDir, "mods" ).getCanonicalFile }
      .filter( _.isDirectory )


  private[ this ] def loadLibraries( classLoader: LaunchClassLoader, dependsJson: DependsJson ): Unit = {
    def loadLibrary( data: LibraryData, path: File ): Unit = {

      if ( Option( data.getClientreq ).forall( _.booleanValue ) )
        if ( FMLLaunchHandler.side().isClient ) classLoader.addURL( path.toURI.toURL )

      if ( Option( data.getServerreq ).forall( _.booleanValue ) )
        if ( FMLLaunchHandler.side().isServer ) classLoader.addURL( path.toURI.toURL )

    }

    def downloadLibrary( data: MavenData, pathname: String, path: File ): Boolean = {
      Option( data.getUrl )
        .map( repositoryAlias )
        .map( new URI( _ ) )
        .map( _.resolve( pathname ) )
        .map( _.toURL )
        .foreach( FileUtils.copyURLToFile( _, path ) )

      if ( !path.isFile ) false
      else {
        val bytes = FileUtils.readFileToByteArray( path )
        val complete = Option( data.getChecksum )
          .map( x => ( x.getMd5, x.getSha1 ) )
          .collect {
            case ( md5, null )  =>
              DigestUtils.md5Hex( bytes ) == md5
            case ( null, sha1 ) =>
              DigestUtils.sha1Hex( bytes ) == sha1
            case ( md5, sha1 )  =>
              DigestUtils.md5Hex( bytes )  == md5  &&
              DigestUtils.sha1Hex( bytes ) == sha1
          }
          .getOrElse( true )

        if ( complete ) {
          val sha = new File( path.getCanonicalPath + ".sha" )
          FileUtils.write( sha, DigestUtils.sha1Hex( bytes ), Charsets.UTF_8 )
        }

        complete
      }
    }

    Option( dependsJson.getLibraries )
      .map( _.toArray( Array.empty[ LibraryData ] ) )
      .foreach( _.filter( _.getMaven ne null )
        .foreach { data =>
          val maven = data.getMaven

          val path = s"${ maven.getGroupId.replace( '.', '/' ) }/${ maven.getArtifactId }/${ maven.getVersion }"
          val name = s"${ maven.getArtifactId }-${ maven.getVersion }.jar"

          val libFile = libraryDir.map( new File( _, s"$path/$name" ).getCanonicalFile )

          if ( libFile.exists( _.isFile ) )
            loadLibrary( data, libFile.get )
          else
            if ( downloadLibrary( maven, s"$path/$name", libFile.get ) ) loadLibrary( data, libFile.get )

        } )
  }


  override def getLaunchTarget: String = null
  override def acceptOptions( args: JavaList[ String ], gameDir: File, assetsDir: File, profile: String ): Unit = {}
  override def getLaunchArguments: Array[ String ] = Array.empty


  override def injectIntoClassLoader( classLoader: LaunchClassLoader ): Unit = {

    modsDir match {
      case None        =>
        FMLLog.severe( "Failed to discover the mods directory" )

      case Some( dir ) =>
        Option( dir.listFiles() )
          .getOrElse( Array.empty )
          .filter( path => path.isFile && path.getName.endsWith( ".jar" ) )
          .map( path => ( path, new JarFile( path ) ) )
          .foreach { case ( path, jar ) =>
            implicit def obj2Opt[ A ]( obj: A ): Option[ A ] = Option( obj )

            Option( jar.getManifest )
              .flatMap( _.getMainAttributes )
              .flatMap( _.getValue( "DependsJson" ) )
              .flatMap( jar.getEntry )
              .flatMap( jar.getInputStream )
              .map( IOUtils.toString( _, Charsets.UTF_8 ) )
              .map( new Gson().fromJson( _, classOf[ DependsJson ] ) )
              .foreach( loadLibraries( classLoader, _ ) )

            jar.close()
          }
    }

  }

}
