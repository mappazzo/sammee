/*
 * This code is property of Mappazzo and Essential Environmental.
 * You should not attempt to de-code, copy or emulate this code.
 *
 * If you have found yourself here then your probably trying to do something
 * that we don't want you to.... but you probably don't really care.
 * Take note that we take our intellectual property seriously and if you are found
 * to be in breach copyright we will pursue you for damages including loss of
 * income or potential earnings.
 *
 */

var licPass = "**password**";
var licURL = "license.php"

// Initial Setup and Control
function lic_Setup() {
	// posterFile = "resources/poster.jpg"
	var Logo1 = document.getElementById("fullBanner");
    	Logo1.src = "sitefiles/logo_250x100.png";
    	Logo1.width = 250;
    	Logo1.height = 100;
	var Logo2 = document.getElementById("shortBanner");
	Logo2.src = "sitefiles/logo_200x40.png";
	Logo2.width = 200;
    	Logo2.height = 40;
    	var Logo3 = document.getElementById("shortBanner2");
	Logo3.src = "sitefiles/logo_200x40.png";
	Logo3.width = 200;
    	Logo3.height = 40;

	var XMLclient1 = new XMLHttpRequest();
	XMLclient1.open('GET', 'admin.p');
	XMLclient1.onloadend = function() {
	  	var lines = XMLclient1.responseText.split("\n");
	  	for(i = 0; i < lines.length; i++){
            		//console.log(lines[i]);
            		var lineData = lines[i].split(",");
            		if(lineData[0] == "pass") licPass = lineData[1];
        	}
        	document.getElementById("access").style.display = 'block';
	}
	XMLclient1.send();
  changeAction()
}
function dbAction() {
	var dbActionForm = document.getElementById("dbAction");
	var action = dbActionForm.elements["action"].value;
	var licNum = dbActionForm.elements["licNum"].value;
	var setVal = dbActionForm.elements["setVal"].value;

	if(action == "newLic") { lic_newLic(parseInt(licNum),setVal);
	} else if (action == "reload") { lic_LoadDB();
	} else { postRequest(action,licNum,setVal);
	}
}
function lic_newLic(propNum, email) {
    if(isNaN(propNum)) propNum = Math.round(Math.random() * 100000);
    postRequest("newLic",propNum,email);
}
function lic_Pass() {
	var userPassword = document.getElementById("password").value;
	if(userPassword == licPass){
		document.getElementById("logo").style.display = 'block';
		document.getElementById("maintForm").style.display = 'block';
		document.getElementById("access").style.display = 'none';
		lic_LoadDB();
	} else {
		document.getElementById("errorMessage").innerHTML = "Incorrect password";
		lic_ShowHide("errorScreen");
	}
}
function lic_LoadDB() {
    var req = new XMLHttpRequest();
    req.open("POST", licURL, true);
    req.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
    req.onreadystatechange = function() {
   	if (req.readyState == 4 && req.status == 200) {
   	    var dbResult = req.responseText;
   	    var dbTable = document.getElementById('dbTable');
    	    var lines = dbResult.split("<br>");
    	    clear_DbTable();
    	    var row;
    	    var cell;
    	    var lineData;

    	    for(i = 2; i < lines.length; i++){
                row = dbTable.insertRow();
                lineData = lines[i].split(",");
                for(c = 0; c < 9; c++) {
                   cell = row.insertCell(c);
                   cell.innerHTML = lineData[c];
                }

                //console.log(lines[i]);
    	    }
   	    //document.getElementById("database").innerHTML = dbResult;
  	}
    };
    var post = licQuery("getDb","0","0");
    //console.log(post);
    req.send(post);
}
function clear_DbTable() {
    var dbTable = document.getElementById('dbTable');
    var rowCount = dbTable.rows.length;
    for(var r=rowCount-1; r>0; r--) {
    	dbTable.deleteRow(r);
    }
}
function changeAction() {
  var dbActionForm = document.getElementById("dbAction");
	var action = dbActionForm.elements["action"].value;
  var numCell = "num_cell";
  var numLabel = document.getElementById("num_label");
  var textCell = "text_cell";
  var textLabel = document.getElementById("text_label");
  lic_Hide(numCell)
  lic_Hide(textCell)

  switch(action) {
    case 'newLic':
      numLabel.innerHTML = 'License No. (opt)';
      textLabel.innerHTML = 'Email address';
      lic_ShowInline(numCell);
      lic_ShowInline(textCell);
      dbActionForm.elements["licNum"].value = null;
      dbActionForm.elements["setVal"].value = null;
      break;
    case "setType":
      numLabel.innerHTML = 'License No.';
      textLabel.innerHTML = 'Type (FULL, FREE, DEMO)';
      lic_ShowInline(numCell);
      lic_ShowInline(textCell);
      dbActionForm.elements["setVal"].value = null;
      break;
    case "setExp":
      numLabel.innerHTML = 'License No.';
      textLabel.innerHTML = 'New expiry (yyyy.mm.dd)';
      lic_ShowInline(numCell);
      lic_ShowInline(textCell);
      dbActionForm.elements["setVal"].value = null;
      break;
    case "setEmail":
      numLabel.innerHTML = 'License No.';
      textLabel.innerHTML = 'Email address';
      lic_ShowInline(numCell);
      lic_ShowInline(textCell);
      dbActionForm.elements["setVal"].value = null;
      break;
    case "resetUser":
      numLabel.innerHTML = 'License No.';
      lic_ShowInline(numCell);
      dbActionForm.elements["setVal"].value = null;
      break;
    case "delLic":
      numLabel.innerHTML = 'License No.';
      lic_ShowInline(numCell);
      dbActionForm.elements["setVal"].value = null;
      break;
    case "reload":
      break;
    default:
  }
  console.log(action);
}
function lic_ShowHide(setDiv_) {
    var setDiv = document.getElementById(setDiv_);
    if (setDiv.style.display === 'none') {
        setDiv.style.display = 'block';
    } else {
        setDiv.style.display = 'none';
    }
}
function lic_Show(setDiv_) {
    var setDiv = document.getElementById(setDiv_);
    setDiv.style.display = 'block';
}
function lic_ShowInline(setDiv_) {
    var setDiv = document.getElementById(setDiv_);
    setDiv.style.display = 'inline';
}
function lic_Hide(setDiv_) {
    var setDiv = document.getElementById(setDiv_);
    setDiv.style.display = 'none';
}
function licQuery(action,license,value) {
    return "action=" + action + "&licNum=" + license + "&setVal=" + value;
}
function postRequest(action,license,value) {
    var post = licQuery(action,license,value);
    var req = new XMLHttpRequest();
    req.open("POST", licURL, true);
    req.setRequestHeader("Content-type", "application/x-www-form-urlencoded");
    req.onreadystatechange = function() {
   	if (req.readyState == 4 && req.status == 200) {
   	    var dbResult = req.responseText.split(",");
   	    document.getElementById("errorMessage").innerHTML = "Request Processed"
   	    document.getElementById("errorDescription").innerHTML = "Action: " + action + ", License: " + license + ", New value: " + value + "<br>Server response: " + dbResult[1];
  	    lic_LoadDB();
  	    lic_ShowHide("errorScreen");
  	}
    };
    req.send(post);
}
