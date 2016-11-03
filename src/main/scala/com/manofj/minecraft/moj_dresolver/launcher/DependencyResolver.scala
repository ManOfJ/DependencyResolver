package com.manofj.minecraft.moj_dresolver.launcher

import java.io.File
import java.util.jar.Attributes
import java.util.jar.JarFile
import java.util.{ List => JavaList }

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.apache.commons.io.Charsets
import org.apache.commons.io.IOUtils
import org.apache.logging.log4j.LogManager

import net.minecraft.launchwrapper.ITweaker
import net.minecraft.launchwrapper.LaunchClassLoader

import net.minecraftforge.fml.relauncher.FMLInjectionData

import com.manofj.minecraft.moj_dresolver.gson.DependsJson
import com.manofj.minecraft.moj_dresolver.resolver.MavenResolver
import com.manofj.minecraft.moj_dresolver.util.Functions._


object DependencyResolver {

  final val OPTION_MINECRAFT_DIR = "moj_dresolver.minecraft.dir"
  final val OPTION_LIBRARIES_DIR = "moj_dresolver.libraries.dir"

  final val ATTRIBUTE_DEPENDS_JSON = new Attributes.Name( "DependsJson" )


  final val logger = LogManager.getLogger( "DependencyResolver" )

  final val resolvers = Seq( MavenResolver )

  final val actualLibrariesDirectory = {
    val extractor = ".*file:/(.+?)/com.*".r
    tryToOption( classOf[ com.google.gson.Gson ].getResource( "Gson.class" ) )
      .map( _.toString )
      .collect { case extractor( path ) => path }
      .flatMap( newCanonicalFile( _ ).filter( _.isDirectory ) )
  }


  def debug( message: String, params: AnyRef* ): Unit = logger.debug( message, params: _* )
  def error( message: String, params: AnyRef* ): Unit = logger.error( message, params: _* )
  def info( message: String, params: AnyRef* ): Unit = logger.info( message, params: _* )
  def trace( message: String, params: AnyRef* ): Unit = logger.trace( message, params: _* )
  def warn( message: String, params: AnyRef* ): Unit = logger.warn( message, params: _* )

}

class DependencyResolver
  extends ITweaker
{
  import com.manofj.minecraft.moj_dresolver.launcher.DependencyResolver._
  import com.manofj.minecraft.moj_dresolver.util.ImplicitConversions._


  private[ this ] var minecraftDirectory = Option.empty[ InjectionData.MinecraftDirectory ]
  private[ this ] var librariesDirectory = Option.empty[ InjectionData.LibrariesDirectory ]
  private[ this ] var modsDirectory      = Option.empty[ InjectionData.ModsDirectory ]
  private[ this ] var defaultLibraries   = Option.empty[ InjectionData.DefaultLibraries ]


  // 各インジェクションデータの生成
  override def acceptOptions( args: JavaList[ String ], gameDir: File, assetsDir: File, profile: String ): Unit = {
    def newCanonicalDir = newCanonicalFile( _: File, _: String ).filter( _.isDirectory )
    def pathToDir = newCanonicalFile( _: String ).filter( _.isDirectory )


    minecraftDirectory =
      pathToDir( System.getProperty( OPTION_MINECRAFT_DIR ) )
        .orElse( pathToDir( s"${ System.getenv( "APPDATA" ) }/.minecraft" ) )
        .map( InjectionData.MinecraftDirectory )

    minecraftDirectory match {
      case Some( dir ) => DependencyResolver.debug( s"Minecraft directory is found ${ dir.data }" )
      case None        => throw new RuntimeException( "Minecraft directory not found" )
    }


    librariesDirectory =
      pathToDir( System.getProperty( OPTION_LIBRARIES_DIR ) )
        .orElse( minecraftDirectory.flatMap( x => newCanonicalDir( x.data, "libraries" ) ) )
        .map( InjectionData.LibrariesDirectory )

    librariesDirectory match {
      case Some( dir ) => DependencyResolver.debug( s"Libraries directory is found ${ dir.data } ( Actual: ${ actualLibrariesDirectory.getOrElse( "NONE" ) } )" )
      case None        => throw new RuntimeException( "Libraries directory not found" )
    }


    modsDirectory =
      gameDir.?
        .orElse( tryToOption( FMLInjectionData.data()( 6 ).asInstanceOf[ File ] ) )
        .flatMap( x => newCanonicalDir( x, "mods" ) )
        .map( InjectionData.ModsDirectory )

    modsDirectory match {
      case Some( dir ) => DependencyResolver.debug( s"Mods directory is found ${ dir.data }" )
      case None        => throw new RuntimeException( "Mods directory not found" )
    }


    val libsDirPath = actualLibrariesDirectory
      .orElse( librariesDirectory.map( _.data ) )
      .map( _.getPath + File.separatorChar )
      .getOrElse( throw new InternalError( "Must be non null" ) )

    defaultLibraries =
      System.getProperty( "java.class.path" ).?
        .map( _.split( ';' ).iterator
               .filter( _.startsWith( libsDirPath ) )
               .flatMap( newCanonicalFile( _ ).map( _.getPath ) )
               .map( _.replace( libsDirPath, "" ) ) )
        .map( _.toSeq )
        .map( InjectionData.DefaultLibraries )

    defaultLibraries match {
      case Some( libs ) => DependencyResolver.debug( s"Minecraft default libraries ${ libs.data.mkString( ";" ) }" )
      case None         => throw new RuntimeException( "Minecraft default libraries undefined" )
    }

  }

  override def injectIntoClassLoader( classLoader: LaunchClassLoader ): Unit = {
    for { mcDir       <- minecraftDirectory
          libsDir     <- librariesDirectory
          modsDir     <- modsDirectory
          defaultLibs <- defaultLibraries
    } {
      def mods =
        modsDir.data.listFiles.?
          .map( _.filter( x => x.isFile && x.getName.endsWith( ".jar" ) ) )
          .map( _.toSeq )
          .getOrElse( Seq.empty )

      val launchClassLoader = InjectionData.LaunchClassLoader( classLoader )
      val injectionData = Seq( mcDir, libsDir, modsDir, defaultLibs, launchClassLoader )

      DependencyResolver.resolvers.foreach( _.inject( injectionData ) )

      var knownMods = Seq.empty[ File ]
      var diff = mods.diff( knownMods )
      while ( diff.nonEmpty )
      {
        diff.foreach { mod =>
          DependencyResolver.debug( s"Check dependencies from ${ mod.getName }" )

          tryToEither( new JarFile( mod ) ) match {
            case Left( e ) =>
              DependencyResolver.warn( s"Failed to open file ${ mod.getAbsolutePath }" )

            case Right( jar ) =>
              for { manifest   <- jar.getManifest.?
                    attributes <- manifest.getMainAttributes.?
                    attribute  <- attributes.getValue( DependencyResolver.ATTRIBUTE_DEPENDS_JSON ).?
                    zipEntry   <- jar.getEntry( attribute ).?

              } tryToEither( jar.getInputStream( zipEntry ) ) match {
                case Left( e ) =>
                  DependencyResolver.warn( s"Error reading from file ${ mod.getAbsolutePath }" )

                case Right( input ) =>
                  DependencyResolver.info( s"Attempts to resolve dependencies for ${ mod.getName }" )

                  val json = IOUtils.toString( input, Charsets.UTF_8 )
                  val dependsJson = new Gson().fromJson( json, classOf[ DependsJson ] )

                  DependencyResolver.trace( s"DependsJSON data:\n${
                    new GsonBuilder().setPrettyPrinting().create.toJson( dependsJson )
                  }" )

                  DependencyResolver.resolvers
                    .withFilter( _.resolvable( dependsJson ) )
                    .foreach { _.process( dependsJson ) match {
                      case Left( e ) =>
                        DependencyResolver.warn( s"Failed to resolver process of ${ mod.getName }\n${ e.getStackTrace.mkString( "\n" ) }" )

                      case Right( message ) =>
                        DependencyResolver.info( s"Successfully resolver process ${ message }" )

                    } }

                  IOUtils.closeQuietly( input )
              }

              tryQuietly( jar.close() )
          }

          DependencyResolver.debug( s"Process for ${ mod.getName } has been completed" )
        }

        knownMods ++= diff
        diff = mods.diff( knownMods )
      }

      DependencyResolver.resolvers.foreach( _.finish() )
    }
  }


  override def getLaunchTarget: String = null
  override def getLaunchArguments: Array[ String ] = Array.empty

}
