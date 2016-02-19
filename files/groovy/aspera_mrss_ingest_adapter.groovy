import com.theplatform.ingest.data.objects.WorkflowOption


def capitalize(s) { s[0].toUpperCase() + s[1..-1].toLowerCase() }
def replaced_title = media.title.replace("_"," ")
media.title = replaced_title
media.description = "description: " + media.title

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
argMap["profile"] = "Accelerate"

// Add the arguments to the workflow request.
wkflwOpt.setArguments(argMap)

// Assign the workflow request to the media. 
def woArr = new WorkflowOption[1]
woArr[0] = wkflwOpt
media.setWorkflowOptions(woArr)