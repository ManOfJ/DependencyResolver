package com.manofj.minecraft.moj_dresolver.resolver

import java.io.File
import java.net.{ URI, URL }

import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.{ Charsets, FileUtils, IOUtils }

import net.minecraftforge.fml.common.FMLLog
import net.minecraftforge.fml.relauncher.{ FMLLaunchHandler, Side }

import com.manofj.minecraft.moj_dresolver.gson.{ DependsJson, LibraryData, MavenData }
import com.manofj.minecraft.moj_dresolver.launcher.InjectionData


class MavenResolver
  extends DependencyResolver
{
  import scala.collection.convert.WrapAsScala
  import com.manofj.minecraft.moj_dresolver._


  // 特定の文字列をMavenのセントラルリポジトリURLに変換するマップ
  private[ this ] final val repositoryAlias = {
    val builder = Map.newBuilder[ String, Symbol ]
    val central = Symbol( "http://central.maven.org/maven2/" )
    builder += ""             -> central
    builder += "central"      -> central
    builder += "default"      -> central
    builder += "mavencentral" -> central
    builder.result.withDefault( Symbol( _ ) )
  }

  // 'グループID:アーティファクトID:バージョン'書式の文字列を分割する正規表現
  private[ this ] final val libInfoRegex = "^([^:]+):([^:]+):(.+)$".r

  // ファイル名からライブラリのバージョン情報を取得する正規表現
  private[ this ] final val libVersionRegex = "^(.+)(?:-([\\d.]+(?:-.+|))\\.jar)$".r


  // ライブラリの情報を表す型エイリアス
  // ( グループID, アーティファクトID, バージョン )
  type LibraryInfo = ( String, String, String )

  // ライブラリのアーティファクトIDに紐づけられた
  // バージョンを返すマップの型エイリアス
  type VersionMap = Map[ String, String ]


  private[ this ] var librariesDir = Option.empty[ File ]
  private[ this ] var defaultLibs = Option.empty[ VersionMap ]
  private[ this ] var resolvedLibs = Option.empty[ VersionMap ]


  private[ this ] def libraryInfo( data: MavenData ): Option[ LibraryInfo ] =
    data.getName.? match {
      case Some( libInfoRegex( groupId, artifactId, version ) ) =>
        ( groupId, artifactId, version ).?

      case _ =>
        for {
          groupId    <- data.getGroupId.?
          artifactId <- data.getArtifactId.?
          version    <- data.getVersion.?

          if groupId.nonEmpty    &&
             artifactId.nonEmpty &&
             version.nonEmpty
      } yield ( groupId, artifactId, version )
    }

  private[ this ] def relativePath( info: LibraryInfo ): String =
    s"${ info._1.replace( '.', '/' ) }/${ info._2 }/${ info._3 }/${ info._2 }-${ info._3 }.jar"

  private[ this ] def libraryVersion( fileName: String ): Option[ ( String, String ) ] =
    fileName match {
      case libVersionRegex( artifactId, version ) => ( artifactId, version ).?
      case _ => None
    }

  private[ this ] def checkConflict( info: LibraryInfo ): Unit = {
    ( for {
      defaults <- defaultLibs
      resolved <- resolvedLibs
    } yield {
      val ( _, artifactId, version ) = info
      defaults.get( artifactId ).foreach { defVersion =>
        if ( version != defVersion ) {
          val msg = "Required library is conflict with default library:"
          val details = s"'$artifactId'-[ default: $defVersion, required: $version ]"
          FMLLog.severe( s"[ MavenResolver ] $msg $details" )

          throw new RuntimeException( "Required library is conflict." )
      } }
      resolved.get( artifactId ).foreach { resVersion =>
        if ( version != resVersion ) {
          val msg = "Required library is conflict with library that other mod required."
          val details = s"'$artifactId'-[ other-mod: $resVersion, required: $version ]"
          FMLLog.severe( s"[ MavenResolver ] $msg $details" )

          throw new RuntimeException( "Required library is conflict." )
      } }
    } )
    .getOrElse( throw new IllegalStateException( "Data inject has not done." ) )
  }


  override def injectData( data: Seq[ InjectionData[ _ ] ] ): Unit = {
    import com.manofj.minecraft.moj_dresolver.launcher.InjectionData._

    data.foreach {
      case LibrariesDir( dir ) if librariesDir.isEmpty =>
        librariesDir = dir.?

      case DefaultLibraries( libs ) if defaultLibs.isEmpty =>
        val builder = Map.newBuilder[ String, String ]
        libs.foreach { x => libraryVersion( x ) match {
          case Some( libVersion ) => builder += libVersion
          case None =>
            FMLLog.warning( s"[ MavenResolver ] Library version parse error -> $x" )
        } }
        defaultLibs = builder.result().?

      case ResolvedURLs( urls ) =>
        val builder = Map.newBuilder[ String, String ]
        urls.foreach { x => libraryVersion( x.getFile ) match {
          case Some( libVersion ) => builder += libVersion
          case None =>
            FMLLog.warning( s"[ MavenResolver ] Library version parse error -> $x" )
        } }
        resolvedLibs = builder.result().?

      case _ =>
  } }

  override def resolvable( json: DependsJson ): Boolean =
    json.getLibraries.?
      .map( WrapAsScala.asScalaBuffer )
      .exists( _.exists( _.getMaven.?.isDefined ) )


  override def resolve( json: DependsJson ): Either[ String, Seq[ URL ] ] = {
    def isRequestSide( data: LibraryData ): Boolean =
      FMLLaunchHandler.side match {
        case Side.CLIENT => data.getClientreq.?.forall( _.booleanValue )
        case Side.SERVER => data.getServerreq.?.forall( _.booleanValue )
      }

    def download( data: MavenData, pathname: String, file: File ): Boolean = {

      data.getUrl.?
        .map( repositoryAlias )
        .orElse( repositoryAlias.get( "" ) )
        .map( x => new URI( x.name ).resolve( pathname ) )
        .fold( false ) { uri =>
          FileUtils.copyURLToFile( uri.toURL, file )

          if ( !file.isFile ) false
          else {
            FMLLog.info( s"[ MavenResolver ] Library has been downloaded: [ $uri -> $file ]" )

            val checksum = data.getChecksum.? match {
              case Some( x ) => ( x.getMd5.?, x.getSha1.? )
              case None =>
                ( tryToOption ( IOUtils.toString( uri.resolve( ".md5" ), Charsets.UTF_8 ) ),
                  tryToOption ( IOUtils.toString( uri.resolve( ".sha1" ), Charsets.UTF_8 ) ) )
            }

            val bytes = FileUtils.readFileToByteArray( file )
            val complete = checksum match {
              case ( Some( md5 ), Some( sha1 ) ) => DigestUtils.md5Hex( bytes )  == md5 &&
                                                    DigestUtils.sha1Hex( bytes ) == sha1
              case ( Some( md5 ), None         ) => DigestUtils.md5Hex( bytes )  == md5
              case ( None,        Some( sha1 ) ) => DigestUtils.sha1Hex( bytes ) == sha1
              case ( None,        None         ) => true
            }

            if ( complete ) {
              val sha1File = new File( file + ".sha" ).getCanonicalFile
              FileUtils.write( sha1File, DigestUtils.sha1Hex( bytes ), Charsets.UTF_8 )
              FMLLog.finer( s"[ MavenResolver ] Created sha1 hash file -> $sha1File" )
            } else {
              file.deleteOnExit()
              FMLLog.warning( s"[ MavenResolver ] Checksum error, delete a file -> $file" )
            }
            complete
        } }

    }


    var resolveUrls = Seq.empty[ URL ]
    json.getLibraries.?
      .map( WrapAsScala.asScalaBuffer )
      .foreach( _
        .withFilter( _.getMaven.?.isDefined )
        .withFilter( isRequestSide )
        .foreach { data =>

          val maven = data.getMaven
          libraryInfo( maven ) match {
            case None =>
              import maven._
              val details = s"[ name = $getName, groupId = $getGroupId," +
                            s" artifactId = $getArtifactId, version = $getVersion ]"
              return Left( s"Illegal Maven data $details" )

            case Some( libInfo ) =>
              checkConflict( libInfo )

              val pathname = relativePath( libInfo )
              val libraryFile = new File( librariesDir.get, pathname ).getCanonicalFile

              if ( libraryFile.isFile || download( maven, pathname, libraryFile ) )
                resolveUrls +:= libraryFile.toURI.toURL
          }
      } )

    Right( resolveUrls )
  }

  override def requiresContinue: Boolean = false

  override def requiresUrlRegister: Boolean = true

}
