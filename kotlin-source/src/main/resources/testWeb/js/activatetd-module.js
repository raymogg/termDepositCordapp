"use strict";

// --------
// WARNING:
// --------

// THIS CODE IS ONLY MADE AVAILABLE FOR DEMONSTRATION PURPOSES AND IS NOT SECURE!
// DO NOT USE IN PRODUCTION!

// FOR SECURITY REASONS, USING A JAVASCRIPT WEB APP HOSTED VIA THE CORDA NODE IS
// NOT THE RECOMMENDED WAY TO INTERFACE WITH CORDA NODES! HOWEVER, FOR THIS
// PRE-ALPHA RELEASE IT'S A USEFUL WAY TO EXPERIMENT WITH THE PLATFORM AS IT ALLOWS
// YOU TO QUICKLY BUILD A UI FOR DEMONSTRATION PURPOSES.

// GOING FORWARD WE RECOMMEND IMPLEMENTING A STANDALONE WEB SERVER THAT AUTHORISES
// VIA THE NODE'S RPC INTERFACE. IN THE COMING WEEKS WE'LL WRITE A TUTORIAL ON
// HOW BEST TO DO THIS.



const app = angular.module('ActivateTDAppModule', ['ui.bootstrap']);

// Fix for unhandled rejections bug.
app.config(['$qProvider', function ($qProvider) {
    $qProvider.errorOnUnhandledRejections(false);
}]);

app.controller('ActivateTDAppController', function($http, $location, $uibModal) {
    const demoApp = this;
    // We identify the node.
    const apiBaseURL = "/api/example/";
    document.getElementById("loading").style.display = "none"
    //populate the current offer options
    var pending = [];
    getPending();

    function getPending() {
                    $http.get("/api/term_deposits/deposits").then(function (response) {
                        response.data.states.forEach(function (element) {
                        if (String(element.internalState) == "Pending") {
//                            pending.push("To: " + String(element.to) + "\nEnd Date: "+
//                            String(element.validTill) + "\nAmount: "+ String(element.amount) + "\nInterest: "+
//                            String(element.interest));
                            pending.push(element);
                            //alert("Pushed");
                        } else {
                            //alert(String(element.internalState));
                        }
                        });
                        loadPending();
                });
    }

    function loadPending() {
        var pending_select = document.getElementById("pending_select");
        for (var i = 0; i < pending.length; i++) {
            var option = document.createElement("option");
            option.value = (pending[i]);
            option.innerHTML = "To: " + String(pending[i].to) + "\nEnd Date: "+
                    String(pending[i].endDate) + "\nAmount: "+ String(pending[i].amount) + "\nInterest: "+
                    String(pending[i].percent);
            pending_select.appendChild(option);
        }
    }

    demoApp.confirmActivate = () => {
            //Load in the required data
            var pending_selected = document.getElementById("pending_select");
//            var client = document.getElementById("client_select");
            var selectedDeposit = pending[pending_selected.selectedIndex];
            //Parse options selected and pull the data
            var value = selectedDeposit.amount;
            var offering_institute = extractOrganisationName(selectedDeposit.from);
            var client = extractOrganisationName(selectedDeposit.to);
            var interest_percent = selectedDeposit.percent;
            //TODO: Duration has to be derived from startDate and endDate (its not a field)
            //var duration = selectedDeposit.duration;
           var dateStart = new Date(selectedDeposit.startDate);
           var dateEnd = new Date(selectedDeposit.endDate);
            var customer_anum = selectedDeposit.client.id;
            //TODO: Should we make an API query for details via account num, or should this be done by iterating in javascript? for now second option
            var customer_details = getCustomerName(customer_anum);
            var customer_fname = customer_details[0];
            var customer_lname = customer_details[1];
            var startDate = selectedDeposit.startDate;
            alert(startDate);
            //var actualDate = Date(startDate[0], startDate[1], startDate[2], startDate[3], startDate[4], 0,0).toISOString();
            //Now convert this to format 2007-12-03T10:15:30 (YYYY-MM-DDTHH:MM:SS)
              var url = "/api/term_deposits/activate_td?td_value="+value+"&offering_institute="+offering_institute+"&interest_percent="+interest_percent+
              "&duration="+duration+"&customer_fname="+customer_fname+"&customer_lname="+customer_lname+"&customer_anum="+customer_anum+"start_date="+startDate+
              "client="+client;
              //Display a loading circle
            document.getElementById("loading").style.display = "block"
            $http.post(url).then(function (response) {
                document.getElementById("loading").style.display = "none"
                alert(String(response.data));
                window.location.href = "index.html";
                });
        }

        demoApp.cancel = () => {
            alert("Cancelled");
            window.location.href = "index.html";
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
                        alert("Client "+ element.firstName +" "+ element.lastName);
                        array.push(element.firstName);
                        array.push(element.lastName);

                    } else {
                        //alert(element.uniqueIdentifier.id)
                    }
                });
            });
            return array;
        }
});

