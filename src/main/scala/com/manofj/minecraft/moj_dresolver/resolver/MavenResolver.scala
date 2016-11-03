package com.manofj.minecraft.moj_dresolver.resolver

import java.io.File
import java.net.URI
import java.util.Locale

import com.google.common.base.Strings
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.Charsets
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.apache.commons.lang3.StringUtils

import net.minecraft.launchwrapper.{ LaunchClassLoader => LaunchClazzLoader }

import net.minecraftforge.fml.relauncher.FMLLaunchHandler
import net.minecraftforge.fml.relauncher.Side

import com.manofj.minecraft.moj_dresolver.gson.DependsJson
import com.manofj.minecraft.moj_dresolver.gson.LibraryData
import com.manofj.minecraft.moj_dresolver.gson.MavenData
import com.manofj.minecraft.moj_dresolver.launcher.DependencyResolver
import com.manofj.minecraft.moj_dresolver.launcher.InjectionData


object MavenResolver
  extends Resolver
{

  // Mavenセントラルリポジトリのエイリアス
  private[ this ] final val centralRepositoryAlias = {
    val centralUrl = "http://central.maven.org/maven2/"
    val builder = Map.newBuilder[ String, String ]

    builder += ""             -> centralUrl
    builder += "central"      -> centralUrl
    builder += "default"      -> centralUrl
    builder += "mavencentral" -> centralUrl
    builder.result.withDefault( String.valueOf )
  }

  // 'グループID:アーティファクトID:バージョン'書式の文字列を分割する正規表現
  private[ this ] final val MavenId = "^([^:]+):([^:]+):(.+)$".r


  // MavenのId( グループID, アーティファクトID, バージョン )
  type MavenId = ( String, String, String )

  // ライブラリの情報( アーティファクトID, バージョン )
  type ArtifactInfo = ( String, String )

  // ライブラリのアーティファクトIDに紐づけられたバージョンを返すマップ
  type ArtifactVersions = Map[ String, String ]


  private[ this ] var defaultLibraries      = Map.empty[ String, MavenId ]
  private[ this ] var resolvedLibraries     = Map.empty[ String, MavenId ]
  private[ this ] var libraryNameByMaven    = Map.empty[ String, MavenData ]
  private[ this ] var launchClassLoaderOpt  = Option.empty[ LaunchClazzLoader ]
  private[ this ] var librariesDirectoryOpt = Option.empty[ File ]


  private[ this ] def mvnIdToLibName( id: MavenId ): String = s"${ id._1 }:${ id._2 }"


  override def inject( data: Seq[ InjectionData[ _ ] ] ): Unit = {
    import com.google.common.base.Preconditions.checkNotNull

    import com.manofj.minecraft.moj_dresolver.launcher.InjectionData._


    def parseMavenId( pathname: String ) = {
      def togid( s: String ) = new File( s ).getPath.replace( '/', '.' )

      val file = new File( "", pathname )
      val path = file.getPath.replace( File.separatorChar, '/' )
      val name = file.getName.replace( ".jar", "" )

      ( 0 until name.length )
        .withFilter( name.charAt( _ ) == '-' )
        .map( name.splitAt( _ ) )
        .map( x => ( x, s"${ x._1 }${ x._2.replaceFirst( "-", "/" ) }" ) )
        .withFilter( x => path.contains( x._2 ) )
        .map( x => ( path.take( path.indexOf( x._2 ) ), x._1._1, x._1._2 ) )
        .map( x => ( togid( x._1 ), x._2, x._3.drop( 1 ) ) )
        .headOption
    }

    data.foreach {
      case DefaultLibraries( libs ) =>
        val builder = Map.newBuilder[ String, MavenId ]

        libs.foreach { data => parseMavenId( data ) match {
          case Some( id ) => builder += mvnIdToLibName( id ) -> id
          case None       => DependencyResolver.debug( s"Library version parsing error -> $data" )
        } }

        defaultLibraries = builder.result

      case LaunchClassLoader( loader ) => launchClassLoaderOpt = Some( checkNotNull( loader ) )
      case LibrariesDirectory( dir ) => librariesDirectoryOpt = Some( checkNotNull( dir ) )
      case _ =>
    }
  }

  override def resolvable( dependsJson: DependsJson ): Boolean =
    Option( dependsJson.getLibraries )
      .map( scala.collection.convert.WrapAsScala.asScalaBuffer )
      .exists( _
        .map( x => Option( x.getMaven ) )
        .exists( _.isDefined ) )

  override def process( dependsJson: DependsJson ): Either[ Throwable, String ] = {
    def requestSide( data: LibraryData ): Boolean =
      FMLLaunchHandler.side match {
        case Side.CLIENT => Option( data.getClientreq ).forall( _.booleanValue )
        case Side.SERVER => Option( data.getServerreq ).forall( _.booleanValue )
      }

    def mavenId( data: MavenData ) =
      Strings.nullToEmpty( data.getName ) match {
        case MavenId( groupId, artifactId, version ) =>
          Some( groupId, artifactId, version )

        case _ =>
          for { groupId    <- Option( data.getGroupId )
                artifactId <- Option( data.getArtifactId )
                version    <- Option( data.getVersion )

                if groupId.nonEmpty    &&
                   artifactId.nonEmpty &&
                   version.nonEmpty
          } yield ( groupId, artifactId, version )
      }

    def conflict( libraryName: String, id: MavenId ) = {
      def replaceClassifier( s: String ) =
        if ( !s.contains( '-' ) ) s + ".5"
        else {
          val lower    = s.toLowerCase( Locale.ENGLISH )
          val replaced = Seq( "-alpha"     -> ".0",
                              "-beta"      -> ".1",
                              "-milestone" -> ".2",
                              "-rc"        -> ".3",
                              "-cr"        -> ".3",
                              "-snapshot"  -> ".4",
                              "-ga"        -> ".5",
                              "-final"     -> ".5",
                              "-sp"        -> ".6" )
            .foldLeft( lower ) { case ( x, ( y, z ) ) => x.replaceFirst( y, z ) }

          if ( replaced == lower ) replaced + ".5" else replaced
        }

      def compareVersion( s1: String, s2: String ) =
        if ( s1 == s2 ) 0
        else {
          val p1 = replaceClassifier( s1 ).split( '.' )
          val p2 = replaceClassifier( s2 ).split( '.' )

          val ( _s1, _s2 ) =
            p1.zip( p2 ).map { case ( x, y ) =>
              val max = x.length max y.length
              ( StringUtils.leftPad( x, max, '0' ), StringUtils.leftPad( y, max, '0' ) )
            }
              .unzip

          _s1.mkString.compareTo( _s2.mkString )
        }


      defaultLibraries.get( libraryName ) match {
        case Some( _id ) =>
          compareVersion( _id._3, id._3 ) match {
            case v if v != 0 => Left( new RuntimeException(
              "Required default library version is conflicts " +
              s"[ Library: ${ mvnIdToLibName( id ) }, Default: ${ _id._3 }, Require: ${ id._3 } ]"
            ) )

            case v => DependencyResolver.warn( "Description of default library is unnecessary" )
          }
          true

        case None => resolvedLibraries.get( libraryName ) match {
          case Some( _id ) =>
            compareVersion( _id._3, id._3 ) match {
              case v if v < 0 =>
                DependencyResolver.warn( "Requires newer version library, so replace resolve library version" )
                resolvedLibraries.updated( mvnIdToLibName( id ), id._3 )

              case v if v > 0  =>
                DependencyResolver.warn( "Mod of require old version library" )

              case _ =>
            }
            true

          case None =>
            false

        }

    } }


    Option( dependsJson.getLibraries )
      .map( scala.collection.convert.WrapAsScala.asScalaBuffer )
      .foreach { _
        .withFilter( requestSide )
        .foreach { x =>
          val data = x.getMaven

          mavenId( data ) match {
            case None =>
              val nam = data.getName
              val gid = data.getGroupId
              val aid = data.getArtifactId
              val ver = data.getVersion

              Left( new RuntimeException(
                s"Illegal Maven data: [ Name = $nam, GroupId = $gid, ArtifactId = $aid, Version = $ver ]"
              ) )

            case Some( id ) =>
              val libraryName = mvnIdToLibName( id )

              if ( !conflict( libraryName, id ) ) {
                resolvedLibraries  += libraryName -> id
                libraryNameByMaven += libraryName -> data
              }
          }
      } }

    Right( "MavenResolver OK" )
  }

  override def finish(): Unit = {
    import com.manofj.minecraft.moj_dresolver.util.Functions._


    def checksum( f: File ) = {
      val bytes = FileUtils.readFileToByteArray( f )
      val sha1  = DigestUtils.sha1Hex( bytes )

      tryToOption( IOUtils.toString( f.toURI.resolve( f.getName + ".sha" ), Charsets.UTF_8 ) ).contains( sha1 )
    }

    def download( data: MavenData, path: String, dist: File ) =
      Option( data.getUrl )
        .orElse( Some( "" ) )
        .map( centralRepositoryAlias )
        .map( new URI( _ ).resolve( path ) )
        .fold( false ) { uri =>
          DependencyResolver.info( s"Try download library $uri" )

          FileUtils.deleteQuietly( dist )
          FileUtils.copyURLToFile( uri.toURL, dist )

          if ( !dist.isFile ) {
            DependencyResolver.warn( s"Failed to download library $uri -> $dist" )
            false
          }
          else {
            DependencyResolver.info( s"Library has been downloaded $dist" )

            val digests =
              Option( data.getChecksum ) match {
                case Some( x ) =>
                  ( Option( x.getMd5 ), Option( x.getSha1 ) )

                case None => (
                  tryToOption( IOUtils.toString( uri.resolve( ".md5" ), Charsets.UTF_8 ) ),
                  tryToOption( IOUtils.toString( uri.resolve( ".sha1" ), Charsets.UTF_8 ) )
              ) }

            val bytes   = FileUtils.readFileToByteArray( dist )
            val success = digests match {
              case ( Some( md5 ), Some( sha1 ) ) => DigestUtils.md5Hex( bytes )  == md5 &&
                                                    DigestUtils.sha1Hex( bytes ) == sha1
              case ( Some( md5 ), None         ) => DigestUtils.md5Hex( bytes )  == md5
              case ( None,        Some( sha1 ) ) => DigestUtils.sha1Hex( bytes ) == sha1
              case ( None,        None         ) => true
            }

            if ( success ) {
              val sha1 = new File( dist.toURI.resolve( dist.getName + ".sha" ) )
              FileUtils.write( sha1, DigestUtils.sha1Hex( bytes ), Charsets.UTF_8 )

              DependencyResolver.debug( s"Created sha1 hash -> $sha1" )
            }
            else {
              dist.deleteOnExit()

              DependencyResolver.warn( s"Checksum error, delete a file $dist" )
            }

            success
          }

        }


    resolvedLibraries.foreach { case ( lib, id ) =>
      val pathname = s"${ id._1.replace( '.', '/' ) }/${ id._2 }/${ id._3 }/${ id._2 }-${ id._3 }.jar"
      newCanonicalFile( librariesDirectoryOpt.get, pathname ).foreach { file =>
        if ( ( file.isFile && checksum( file ) ) || download( libraryNameByMaven( lib ), pathname, file ) ) {
          launchClassLoaderOpt.get.addURL( file.toURI.toURL )
          DependencyResolver.info( s"Dependency library resolved ${ file.getAbsolutePath }" )
    } } }
  }

}
