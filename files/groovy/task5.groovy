import com.theplatform.groovy.helper.MediaHelper
import com.theplatform.ingest.adapter.api.AdapterException
import com.theplatform.ingest.adapter.api.AdapterResultItem
import com.theplatform.data.api.objects.type.CategoryInfo
import com.theplatform.data.api.objects.type.DateOnly
import com.theplatform.data.api.objects.type.Duration
import com.theplatform.ingest.data.objects.IngestMedia
import com.theplatform.ingest.data.objects.IngestMediaFile
import com.theplatform.ingest.data.objects.WorkflowOption
import java.net.URI

// The Watch Folder agent adds the name of the source metafile
// to the map of additional parameters. You can read it in your
// script (using the filename key), should you need to record it.
// The path information is relative to watch folder URL.
// The path also includes the filename.
def filename = adapterSettings.getAdditionalParameterMap().get("filename")
def filepath = adapterSettings.getAdditionalParameterMap().get("filePath")

def records

try
{
    // XmlSlurper is an XML parser built into Groovy.
    records = new XmlSlurper().parse(metadata)
}
catch(Exception e)
{
    // If the XML isn't parseable, throw an AdapterException from here.
    // Script execution halts and the error is returned to the caller. 
    throw new AdapterException(e)
}

def items = records.item

items.each
{
    // If the metadata has what we want, this will remain null.
    Exception ex = null

    // Create a new IngestMedia item. We'll populate 
    // its fields with elements from the input XML. 
    IngestMedia media = new IngestMedia()

    Boolean update = false
    Boolean delete = false

    if (it.id != "" || it.guid != "")
    {
        // We may be attempting to do either a delete or update, 
	// so see if the identifier for the media object exists in 
	// the current mpx account. 
	if (it.id != "") 
	{ 
	  if (mediaHelper.mediaExistsById(new URI(it.id.text())))
	  {
	     // We found the identifier in the system
	     update = true
	     media.id = new URI(it.id.text())
	  }
	}
	else // Let's check the guid to see if it exists
	{
	  if (mediaHelper.mediaExistsByGuid(it.guid.text()))
	  {
	     // We found the guid in the system
	     update = true
	  }
	}

	// Either way, we want to set the guid value to save it 
	if (it.guid != "")
	  media.guid = it.guid.text()
	
	if (it.delete == "true")
	{
           // We're deleting. Set delete on the media item 
           // and continue to the next item in the metadata.
           media.deleted = true
           delete = true
	}
    }

    // If we're not deleting, then process the rest of the input data.
    if (!delete)
    {
        // If an element is missing, it.elName returns "" and has a size of zero.
        // If this is the case for a required value on a new item, then create an 
        // adapter exception and attach it to the result item and then add to the queue.
        if (it.short_description != "")
        {
            media.description = it.short_description
        }
        else if (it.long_description != "")
        {
            media.description = it.long_description
        }
        else
        {
            // If we're creating we can't have a blank description. 
            if (!update)
            {
                ex = new Exception ("Media item requires some kind of description.")
            }
        }
    
        // If we haven't created the exception, process the rest of the XML.
        if (ex == null)
        {
            // Start setting properties. 
            if (it.title != "")
                media.title = it.title
    
            if (it.copyright!="")
                media.copyright = it.copyright
    
            // Read the category elements into Category objects.
            // Categories have to match those available to the account context, but if 
            // those don't match mpx will catch that (and all other system-required fields).
            // We only have to worry about our custom exceptions in this script. 
            def catArrList = []
    
            def cats = it.category
            cats.each
            {
                CategoryInfo catInfo = new CategoryInfo()
                catInfo.name = it.text()
                catArrList << catInfo
            }
    
            if (catArrList.size() > 0)
            {
                media.categories = catArrList.toArray(new CategoryInfo[catArrList.size()])
            }
    
            // Next, set the publication and expiration dates for the media
            def pubDateArray = it.publish_date != "" ? it.publish_date.text().split('/') : null
            if (pubDateArray != null)
            {
                def date_only = new DateOnly(Integer.parseInt(pubDateArray[2]),
                                             Integer.parseInt(pubDateArray[0]), 
                                             Integer.parseInt(pubDateArray[1]))

                media.pubDate = date_only.toDate()
            }

            def expDateArray = it.expiration_date != "" ? it.expiration_date.text().split('/') : null
            if (expDateArray != null)
            {
                def date_only = new DateOnly(Integer.parseInt(expDateArray[2]), 
                                         Integer.parseInt(expDateArray[0]), 
                                         Integer.parseInt(expDateArray[1]))

                media.expirationDate = date_only.toDate()
            }

            //Next, see if the custom_data element exists and 
            // copy its value into our custom field (CustomOne).
            if (it.custom_data != "")
            {
                media.setCustomValue(null, "Genre", it.custom_data.text());
            }
    
            // Next find the file_name items and separate the image thumbnail from the source non-image file.
            // We'll assume that there could be multiple of each.
            // You could also add error handling to raise an exception if there is a thumbnail reference
            // but not one for the source (or if there are no file_name elements at all). 
            def ingestMediaFiles = []
            def ingestMediaThumbnailFiles = []
    
            def mediaFileThumbFiles = it.file_name.findAll{it.text().toLowerCase() =~ 
                                        '(\\.jp[e]?g|\\.bmp|\\.png|\\.tif[f]?|\\.gif)$'}

            if (mediaFileThumbFiles.size() > 0)
            {
                mediaFileThumbFiles.each {
                    IngestMediaFile mediaFileThumb = new IngestMediaFile()
                    mediaFileThumb.sourceUrl = it.text()
                    mediaFileThumb.serverId = new URI("http://data.media2.theplatform.com/media/data/Server/538181684")
                    //mediaFileThumb.assetTypes = ["thumbnail"]

                    ingestMediaThumbnailFiles << mediaFileThumb
                }
            }

            // Take the non-image files and assume they're your video files. 
            def mediaFileSourceFiles = it.file_name.findAll{it.text().toLowerCase() =~ 
                                        '.+(?<!(\\.jp[e]?g|\\.bmp|\\.png|\\.tif[f]?|\\.gif))$'}
    
            if (mediaFileSourceFiles.size() > 0)
            {
                mediaFileSourceFiles.each {

                    IngestMediaFile mediaFileSource = new IngestMediaFile()
                    mediaFileSource.sourceUrl = it.text()
                    mediaFileSource.serverId = new URI("http://data.media2.theplatform.com/media/data/Server/538181684")
                    //mediaFileSource.assetTypes = ["source"]
   
                    // Next, grab the run_time and file_size values and assign them to the source mediafile
                    mediaFileSource.duration = new Duration(Integer.parseInt(it.@run_time.text()))
                    mediaFileSource.fileSize = Integer.parseInt(it.@file_size.text())

                    ingestMediaFiles << mediaFileSource
                }
            }
		  
            if (it.publish != ""){
                // Have the ingest service start a publish workflow after the files are ingested
                WorkflowOption wkflwOpt = new WorkflowOption()

                // Set the options for the publish action. Start with the service name.
                wkflwOpt.setService("publish")
                // Then name the method.
                wkflwOpt.setMethod("publish")

                // Next, set the arguments for the publish action. In this case,
                // it's the name of the publish profile, which is set in the 
                // input data. Submit the arguments in a map. 
                def argMap = [:]
                argMap["profile"] = it.publish.text()

                // Add the arguments to the workflow request.
                wkflwOpt.setArguments(argMap)

                // Assign the workflow request to the media. 
                def woArr = new WorkflowOption[1]
                woArr[0] = wkflwOpt
                media.setWorkflowOptions(woArr)
            }

            // Add the media files to the media items.
            if (ingestMediaFiles.size() > 0)
            {
                media.setContent(ingestMediaFiles.toArray(new IngestMediaFile[ingestMediaFiles.size()]))
            }
            if (ingestMediaThumbnailFiles.size() > 0)
            {
                media.setThumbnails(ingestMediaThumbnailFiles.toArray(new IngestMediaFile[ingestMediaThumbnailFiles.size()]))
            }
        }
    }

    // Create the result item and populate it with either the media data 
    // or the exception for a missing description. Then add to the queue.
    AdapterResultItem resultItem = new AdapterResultItem()

    if (ex != null)
    {
        resultItem.setException(ex)
    }
    else
    {
        resultItem.media = media
    }

    resultQueue.put(resultItem)
}