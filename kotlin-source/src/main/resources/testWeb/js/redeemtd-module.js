"use strict";

//App Module for redeeming a term deposit
const app = angular.module('RedeemTDAppModule', ['ui.bootstrap']);

// Fix for unhandled rejections bug.
app.config(['$qProvider', function ($qProvider) {
    $qProvider.errorOnUnhandledRejections(false);
}]);

app.controller('RedeemTDAppController', function($http, $location, $uibModal) {
    const demoApp = this;
    // We identify the node.
    const apiBaseURL = "/api/example/";
    document.getElementById("loading").style.display = "none"
    //populate the current offer options
    var matured = [];
    getMatured();

    //OnClick methods for each button -> used for loading new pages for TD functionality
        demoApp.issueTD = () => {
            window.location.href = "issue_td.html";
        }

        demoApp.activateTD = () => {
                window.location.href = "activate_td.html";
        }

        //Note this will fail if not called from a bank node.
        demoApp.redeemTD = () => {
            window.location.href = "redeem_td.html";
        }

        demoApp.rolloverTD = () => {
            window.location.href = "rollover_td.html"
        }

        demoApp.home = () => {
                            window.location.href = "homepage.html"
                        }

        demoApp.kyc = () => {
            window.location.href = "kyc_info.html"
        }
    //Get all matured term deposits (for our demo purposes this is just any active td)
    function getMatured() {
        $http.get("/api/term_deposits/deposits").then(function (response) {
            response.data.states.forEach(function (element) {
                if (String(element.internalState) == "Active" || String(element.internalState) == "Matured") { //active kept in for demo purposes
                    matured.push(element);
                }
            });
            loadMatured();
        });
    }

    //Display matured term deposits to the user
    function loadMatured() {
        var matured_select = document.getElementById("matured_select");
        for (var i = 0; i < matured.length; i++) {
            var option = document.createElement("option");
            option.value = (matured[i]);
            option.innerHTML = "To: " + String(matured[i].to) + "\nEnd Date: "+
                    String(matured[i].endDate) + "\nAmount: "+ String(matured[i].amount) + "\nInterest: "+
                    String(matured[i].percent);
            matured_select.appendChild(option);
        }
    }

    //OnClick method for redeem button
    demoApp.redeem = () => {
        //Load in the required data
        var matured_select = document.getElementById("matured_select");
        var selectedDeposit = matured[matured_select.selectedIndex];
        //Parse options selected and pull the data
        var value = selectedDeposit.amount;
        var actualValue = stripValue(value); //removes the USD from the string and returns the int
        var offering_institute = extractOrganisationName(selectedDeposit.from);
        var client = extractOrganisationName(selectedDeposit.to);
        var interest_percent = selectedDeposit.percent;
        var duration = getDuration(selectedDeposit.startDate, selectedDeposit.endDate);
        var customer_anum = selectedDeposit.client.id;
        var startDate = selectedDeposit.startDate;
        $http.get("/api/term_deposits/kyc").then(function (response) {
            response.data.kyc.forEach(function (element) {
                if (String(element.uniqueIdentifier.id) == customer_anum) {
                    //This is the correct client
                    callRedeem(actualValue, offering_institute, interest_percent, duration, element.firstName, element.lastName,
                    element.accountNum, startDate, client);
                    return;
                }
             });
        });
    }

        //Redeem API call
        function callRedeem(value, offering_institute, interest_percent, duration, customer_fname, customer_lname, customer_anum, startDate, client) {
            var url = "/api/term_deposits/redeem_td?td_value="+value+"&offering_institute="+offering_institute+"&interest_percent="+interest_percent+
                "&duration="+duration+"&customer_fname="+customer_fname+"&customer_lname="+customer_lname+"&customer_anum="+customer_anum+"&start_date="+startDate //+
                //"&client="+client;
                //Display a loading circle
             //alert(String(url));
            document.getElementById("loading").style.display = "block"
            $http.post(url).then(function successCallback(response) {
                        document.getElementById("loading").style.display = "none"
                        alert(String(response.data));
                        window.location.href = "homepage.html";
                    }, function errorCallback(error) {
                        alert("Error" + String(error.data));
                    });
        }

        demoApp.cancel = () => {
            alert("Cancelled");
            window.location.href = "homepage.html";
        }

        //Helper function to extract the needed organisation name from a formatted Corda Name String
                //This organisatino name is needed to issue a new TD.
    function extractOrganisationName(partyString) {
        var actualName = "";
        var seenEqual = 0;
        //Extract and return the party name by parsing a string
        for(var i = 0; i < partyString.length; i++) {
            if (partyString.charAt(i) == '=') {
                //First instance of seeing = is right after O, so from now till next L we append these to the name
                if (!seenEqual) {
                    seenEqual = 1;
                }
            } else if (partyString.charAt(i) == ',') {//stop parsing the name
                return actualName;
            } else if (seenEqual) {
                actualName += partyString.charAt(i);
            }
        }
    }

    function getCustomerName(accountNum) {
                var array = [];
                $http.get("/api/term_deposits/kyc").then(function (response) {
                    response.data.kyc.forEach(function (element) {
                        if (String(element.uniqueIdentifier.id) == accountNum) {
                            //This is the correct client
                            array.push(element.firstName);
                            array.push(element.lastName);
                            return array;
                        } else {
                            //alert(element.uniqueIdentifier.id)
                        }
                    });
                });
            }

    /** Since JS uses a different min date to Java, a custom duration calcualtor needs to be made */
    function getDuration(startDate, endDate) {
        //Note startDate and end date are strings in the form YYYY-MM-DD
        //Parse both strings
        var startDateYears = parseInt(parseYear(startDate));
        var startDateMonths = parseInt(parseMonth(startDate));
        var endDateYears = parseInt(parseYear(endDate));
        var endDateMonths = parseInt(parseMonth(endDate));

        var diff = (Math.abs(endDateYears - startDateYears) * 12) + Math.abs(endDateMonths - startDateMonths);
        return diff;

    }

    function parseYear(dateString) {
        for (var i = 1; i < dateString.length; i++) {
        //year is all strings before the first "-" -> start at 1 due to the first char being + or - which we can always include
            if (dateString[i] == '-') {
                //found the end of the year section
                return dateString.substring(0,i);
            }
        }
    }

    function parseMonth(dateString) {
        //first get past the first '-'
        var i = 1;
        while (dateString[i] != '-' && i < dateString.length) {
            i++;
        }
        //i is now equal to the index of first '-', increment once for start index of substring
        var startIndex = ++i;
        //get the end index
        while (dateString[i] != '-' && i < dateString.length) {
            i++;
        }
        //i is now equal to the index of first '-', increment once for start index of substring
        var endIndex = i;
        return dateString.substring(startIndex, endIndex);
    }

    function parseDay(dateString) {
        //first get past the first and second '-'
        var i = 1;
        var count = 0;
        while (count != 2 && i < dateString.length) {
            if (dateString[i] == '-') {
                count++;
            }
            i++;
         }
         //i is now equal to the index of second '-', increment once for start index of substring
         var startIndex = ++i;
         //get the end index
         while (dateString[i] != '\n' && i < dateString.length) {
            i++;
         }
         //i is now equal to the index of first '-', increment once for start index of substring
         var endIndex = i;
         return dateString.substring(startIndex, endIndex);
    }

    function stripValue(value) {

        var endIndex = 0;
        for (var i = 0; i < value.length; i++) {
            if (isNaN(value[i]) && value[i] != '.') {
                endIndex = i;
                i = value.length;
            } else {
                //have not found the end index
                i++;
            }
        }
        alert(value.substring(0, endIndex));
        return parseFloat(value.substring(0,endIndex));
    }



});

