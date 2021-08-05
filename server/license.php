<?php
/*
    Created on : 05/03/2016
    Author     : kelly@mappazzo.com
*/
echo "SammEE License Server,";

// MySQL server settings
$serverName = "localhost";
$dbName = "urur3975_samee";
$dbLicTable = "userdata";
$dbUsername = "urur3975_user1";
$dbPassword = "yuMmYbraIN$4=6";

//receive parameters via POST
foreach ($_POST as $key => $value) {
    switch ($key) {
        case 'action':
            $action = $value;
            break;
        case 'licNum':
            $license = $value;
            break;
        case 'setVal':
            $setValue = $value;
            break;
        default:
            break;
    }
}
// echo "set values: license = $license, action = $action, value = $setValue<br>";


$conn = mysqli_connect($serverName, $dbUsername, $dbPassword, $dbName);
if (!$conn) {
  die("dbError," . mysqli_connect_error());
}

// initial SQLquery
if($action=="getDb") {
    $sql = "SELECT * FROM $dbLicTable";
    $dbReturnData = $conn->query($sql);
} else if($action=="newLic") {
    $daySec = 86400;
    $newExp = date('Y.m.d', time() + $daySec * 60);
    $sql = "INSERT INTO $dbLicTable (licNumber, userEmail, licType, version, userLogin, expiry, runCount, lastReport, installCount) VALUES ('$license','$setValue','DEMO',0,'','$newExp',0,'',0)";
    if($conn->query($sql) === TRUE) {
	     die("newLicense Success exp:" . $newExp);
    } else {
	     die("dbError," . $conn->error . "," . $sql);
    }
} else {
    $sql = "SELECT * FROM $dbLicTable WHERE licNumber = $license";
    $dbReturnData = $conn->query($sql);
    if (mysqli_num_rows($dbReturnData) != 1) {
        die("badLicense");
    } else {
        $licData = mysqli_fetch_assoc($dbReturnData);
    }
}

switch ($action) {
    case "getDb":
    	echo "current records"."<br>";
    	echo "licNumber,licType,version,userEmail,userLogin,expiry,runCount,lastReport,installCount";
    	if ($dbReturnData->num_rows > 0) {
    		// output data of each row
    		while($licData = $dbReturnData->fetch_assoc()) {
        		echo "<br>" . $licData["licNumber"] . "," . $licData["licType"]
        		. "," . $licData["version"] . "," . $licData["userEmail"]
        		. "," . $licData["userLogin"]  . "," . $licData["expiry"]
        		. "," . $licData["runCount"]  . "," . $licData["lastReport"] . "," . $licData["installCount"];
    		}
    	}
      break;
    case "getLic":
      echo $licData["licNumber"] . "," . $licData["licType"]
      . "," . $licData["version"] . "," . $licData["userEmail"]
      . "," . $licData["userLogin"]  . "," . $licData["expiry"]
      . "," . $licData["runCount"]  . "," . $licData["lastReport"];
      break;
    case "setUser":
    	if ($licData["userLogin"] == "") {
    	    $sql = "UPDATE $dbLicTable SET userLogin='$setValue' WHERE licNumber=$license";
    	    if($conn->query($sql) === TRUE) {
    	    	echo "setUser Success";
    	    } else {
    	    	echo "dbError," . $conn->error . "," . $sql;
    	    }
    	} else {
    	    echo "userExists";
    	}
    	break;
    case "setEmail":
    	$sql = "UPDATE $dbLicTable SET userEmail='$setValue' WHERE licNumber=$license";
    	if($conn->query($sql) === TRUE) {
    	    echo "setEmail Success";
    	} else {
    	    echo "dbError," . $conn->error . "," . $sql;
    	}
    	break;
    case "setType":
	    $sql = "UPDATE $dbLicTable SET licType='$setValue' WHERE licNumber=$license";
      if($conn->query($sql) === TRUE) {
    	    echo "setType Success";
    	} else {
    	    echo "dbError," . $conn->error . "," . $sql;
    	}
	    break;
    case "setExp":
    	$sql = "UPDATE $dbLicTable SET expiry=\"$setValue\" WHERE licNumber=$license";
    	if($conn->query($sql) === TRUE) {
    	    echo "setExpiry Success";
    	} else {
    	    echo "dbError," . $conn->error . "," . $sql;
    	}
	    break;
    case "resetUser":
    	$sql = "UPDATE $dbLicTable SET userLogin=\"\" WHERE licNumber=$license";
    	if($conn->query($sql) === TRUE) {
    	    echo "resetUser Success";
    	} else {
    	    echo "dbError," . $conn->error . "," . $sql;
    	}
	    break;
    case "setRuns":
    	if ($licData["runCount"] == "" || $licData["runCount"] < $setValue) {
    	    $sql = "UPDATE $dbLicTable SET runCount=$setValue,lastReport=\"".date('Y.m.d')."\" WHERE licNumber=$license";
    	    // echo $sql;
    	    if($conn->query($sql) === TRUE) {
    	    	echo "setRuns Success";
    	    } else {
    	    	echo "dbError," . $conn->error;
    	    }
    	} else {
    	    echo "badRuns";
    	}
    	break;
    case "setVer":
    	$sql = "UPDATE $dbLicTable SET version=$setValue WHERE licNumber=$license";
    	if($conn->query($sql) === TRUE) {
    	    echo "setVer Success";
    	} else {
    	    echo "dbError," . $conn->error;
    	}
    	break;
    case "delLic":
    	$sql = "DELETE FROM $dbLicTable WHERE licNumber=$license";
    	if($conn->query($sql) === TRUE) {
    	    echo "delLic Success";
    	} else {
    	    echo "dbError," . $conn->error;
    	}
    	break;
    case "addInst":
    	$sql = "UPDATE $dbLicTable SET installCount=\"". ($licData["installCount"] + 1)."\" WHERE licNumber=$license";
    	if($conn->query($sql) === TRUE) {
    	    echo "addInst Success";
    	} else {
    	    echo "dbError," . $conn->error;
    	}
    	break;
    default:
    	echo "badAction";
}
mysqli_close($conn);
?>
