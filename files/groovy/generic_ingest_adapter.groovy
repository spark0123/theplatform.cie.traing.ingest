/*
 * Chad's Generic Ingest Adapter Tempate
 * Configuration set in parameter map of the adapter config
 */
import com.theplatform.ingest.data.objects.WorkflowOption
import com.theplatform.ingest.data.objects.IngestMediaFile
import com.theplatform.ingest.data.objects.IngestMedia
import com.theplatform.media.api.data.objects.ContentType
import com.theplatform.data.api.objects.type.CategoryInfo
import com.theplatform.media.api.data.objects.Media
import com.theplatform.data.api.objects.FieldInfo

//Setup config before hand.
def Map ingest_config				= adapterSettings.getPropertyMap();
def approveMedia 					= Boolean.valueOf(ingest_config.get("approveMedia"));
def storageServerId 				= ingest_config.get("storageServerId").toURI();
def publishProfile 					= ingest_config.get("publishProfile");

IngestMedia incomingMedia = media

incomingMedia.approved = approveMedia;
//assumes files have a unique name, use this as guid allows for pairing images and video within the same Media
incomingMedia.guid = incomingMedia.title.replace(" ","_");  

def mediaExists = mediaHelper.mediaExistsByGuid(incomingMedia.guid);
def hasImage = false;
def hasVideo = false;
def existingMedia = null;

if (mediaExists){
	existingMedia = mediaHelper.getMediaByGuid(incomingMedia.guid);
	existingMedia.getContent().each() 	{ if (it.contentType == ContentType.video) hasVideo = true; }
	existingMedia.getThumbnails().each(){ if (it.contentType == ContentType.image) hasImage = true; }
}else{
	incomingMedia.title = incomingMedia.guid.replace("_"," "); //Friendly name
}
	
def contentFiles 		= [];
def thumbnailFiles 		= [];
def workflowOptions 	= [];
def thePublishProfile 	= "";

IngestMediaFile[] imfArr = incomingMedia.getContent();

imfArr.eachWithIndex() { imf, i ->
	def sourceUrl 	= imf.sourceUrl;

    IngestMediaFile processedImf = imf;
	
	processedImf.setServerId(storageServerId);
	def fileExt = sourceUrl.substring(sourceUrl.lastIndexOf('.') + 1);
	def imfFormat = formatClient.getByExtension(fileExt);
    def existingAdminTags = (null != existingMedia && null != existingMedia.adminTags) ? existingMedia.adminTags : [] as String[]
	def workflowlabel = existingAdminTags.size() > 0 ? new HashSet<String>(existingAdminTags.toList()) : new HashSet<String>(0)
	
	def assetTypes = [];
	//Add Video Files
	if (imfFormat.defaultContentType == ContentType.video){
		assetTypes.push("Mezzanine");
		thePublishProfile = publishProfile;
		processedImf.assetTypes = assetTypes;
		contentFiles << processedImf;
			
	//Add Image Files
	}else if (imfFormat.defaultContentType == ContentType.image){
		assetTypes.push("Mezzanine");	
		processedImf.assetTypes = assetTypes;
		thumbnailFiles << processedImf;
	}else if (imfFormat.defaultContentType == ContentType.document){
		//for example caption files
		contentFiles << processedImf;
	}	
	incomingMedia.setContent(contentFiles.toArray(new IngestMediaFile[contentFiles.size()]));
	incomingMedia.setThumbnails(thumbnailFiles.toArray(new IngestMediaFile[thumbnailFiles.size()]));
}

if(thePublishProfile){
	workflowOptions << createWorkflowOption("publish", "auto-detect", ["profileId": thePublishProfile]);
}
incomingMedia.setWorkflowOptions(workflowOptions as WorkflowOption[])

private WorkflowOption createWorkflowOption(String service, String method, Map<String,String> arguments)  {
    WorkflowOption workflowOption = new WorkflowOption();
    workflowOption.service = service
    workflowOption.method = method
    workflowOption.arguments = arguments

    return workflowOption
}