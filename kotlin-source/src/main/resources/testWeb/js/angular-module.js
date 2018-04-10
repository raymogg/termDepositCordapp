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


//Main JS App for Term Deposits
const app = angular.module('demoAppModule', ['ui.bootstrap']);

// Fix for unhandled rejections bug.
app.config(['$qProvider', function ($qProvider) {
    $qProvider.errorOnUnhandledRejections(false);
}]);

app.controller('DemoAppController', function($http, $location, $uibModal) {
    const demoApp = this;
    // We identify the node.
    const apiBaseURL = "/api/example/";
    //Variables for TD app
    var activeTDs = [];
    var currentOffers = [];
    //Get nodes name and display in browser
    displayNodeName();

    // First we pull the TD's from the api -> these are displayed on the home screen
    $http.get("/api/term_deposits/deposits").then(function (response) {
            response.data.states.forEach(function (element) {
            activeTDs.push("From: " + String(element.from) + ", Amount: " + String(element.amount) + ", Ending: " +
            String(element.endDate) + ", Internal State: " + String(element.internalState));
            loaded(activeTDs);});
        });

    function loaded(array) {
        var ul = document.createElement('ul');
        ul.setAttribute('id','proList');
        var t, tt;
        document.getElementById('currentDeposits').innerHTML = "<h3> Current Deposits</h3>";
        document.getElementById('currentDeposits').appendChild(ul);
        array.forEach(function (element, index, arr) {
            var li = document.createElement('li');
            li.setAttribute('class','item');

            ul.appendChild(li);

            t = document.createTextNode(element);

            li.innerHTML=li.innerHTML + element;
                 });
    }

    //Next we pull all offers this node has and display these
    $http.get("/api/term_deposits/offers").then(function (response) {
                response.data.offers.forEach(function (element) {
                currentOffers.push("From: " + String(element.issuingInstitute) + ", Interest: " + String(element.interest) + ", Duration: " +
                String(element.duration) + ", Valid Until: " + String(element.validTill));
                loadedOffers(currentOffers);});
            });

        function loadedOffers(array) {
            var ul = document.createElement('ul');
            ul.setAttribute('id','proList');
            var t, tt;
            document.getElementById('currentOffers').innerHTML = "<h3>Current Offers</h3>";
            document.getElementById('currentOffers').appendChild(ul);
            array.forEach(function (element, index, arr) {
                var li = document.createElement('li');
                li.setAttribute('class','item');

                ul.appendChild(li);

                t = document.createTextNode(element);

                li.innerHTML=li.innerHTML + element;
                     });
        }

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



    demoApp.openModal = () => {
        const modalInstance = $uibModal.open({
            templateUrl: 'demoAppModal.html',
            controller: 'ModalInstanceCtrl',
            controllerAs: 'modalInstance',
            resolve: {
                demoApp: () => demoApp,
                apiBaseURL: () => apiBaseURL,
            }
        });

        modalInstance.result.then(() => {}, () => {});
    };

    function displayNodeName() {
        //Make the API Call then change the title
        var url = "/api/example/me"
        $http.get(url).then(function (response) {
            var title = document.getElementById("title").innerHTML = "TD Cordapp: " + extractOrganisationName(response.data.me);
            var name_title = document.getElementById("node_title").innerHTML = "Welcome, " + extractOrganisationName(response.data.me);
        });
    }

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

});

app.controller('IssueTDCtrl', function ($http, $location, $uibModalInstance, $uibModal, demoApp, apiBaseURL, peers) {
    const modalInstance = this;

        modalInstance.offers = getOffers();
        modalInstance.clients = getClients();
        modalInstance.form = {};
        modalInstance.formError = false;

        // Validate and create IOU.
        modalInstance.create = () => {
//            if (invalidFormInput()) {
//                modalInstance.formError = true;
//            } else {
//                modalInstance.formError = false;
//
//                $uibModalInstance.close();
//
//                const createIOUEndpoint = `${apiBaseURL}create-iou?partyName=${modalInstance.form.counterparty}&iouValue=${modalInstance.form.value}`;
//
//                // Create PO and handle success / fail responses.
//                $http.put(createIOUEndpoint).then(
//                    (result) => {
//                        modalInstance.displayMessage(result);
//                        demoApp.getIOUs();
//                    },
//                    (result) => {
//                        modalInstance.displayMessage(result);
//                    }
//                );
//            }
        };

        function getOffers() {
            $http.get("/api/term_deposits/offers").then(function (response) {
                        return response.data.offers;
                    });
        }

        function getClients() {
//        $http.get("/api/term_deposits/offers").then(function (response) {
//                                return response.data.offers;
//                            });
            return ["Client1", "Client2", "Client3"];

        }
});

// Controller for success/fail modal dialogue.
app.controller('messageCtrl', function ($uibModalInstance, message) {
    const modalInstanceTwo = this;
    modalInstanceTwo.message = message.data;
});





//let activeTDs = ["one"];
//
//function getActiveDeposits() {
//
//  // First we pull the TD's from the api
//  $http.get("/api/term_deposits/deposits").then((response) => activeTDs = response);
//
//  var ul = document.createElement('ul');
//   ul.setAttribute('id','proList');
//
//   var t, tt;
//   document.getElementById('currentDeposits').appendChild(ul);
//   activeTDs.forEach(renderProductList);
//
//   function renderProductList(element, index, arr) {
//       var li = document.createElement('li');
//       li.setAttribute('class','item');
//
//       ul.appendChild(li);
//
//       t = document.createTextNode(element);
//
//       li.innerHTML=li.innerHTML + element;
//   }
//}
//
//function clickButton() {
//  alert("Hello");
//}
//
//getActiveDeposits();
