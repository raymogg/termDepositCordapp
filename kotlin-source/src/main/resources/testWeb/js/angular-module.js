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

    // First we pull the TD's from the api
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

    //OnClick methods for each button -> used for creating TDs and what not
    demoApp.issueTD = () => {
        //Go to new html page to issue td
        window.location.href = "issue_td.html";

        //Load in the data
//        var offers = getOffers();
//        alert(offers.length);
//        var test = ["one","two","three"];
//        var offers_select = document.getElementById("offers_select")
//        for (var i = 0; i < test; i++) {
//            var option = document.createElement("option");
//            option.value = String(test[i]);
//            option.innerHTML = String(test[i]);
//            offers_select.appendChild(option);
//        }
    }

    //Note this will fail if not called from a bank node.
    demoApp.activateTD = () => {
            alert("trying to issue td");
            //Show some fields for the user to choose details

             //Execute the http call (for now lets just hardcode and do this.
             var url = "/api/term_deposits/activate_td?td_value=500&offering_institute=BankA&interest_percent=2.55&duration=6&customer_fname=Jane&customer_lname=Doe&customer_anum=9384&start_date=2007-12-03T10:15:30&client=AMM"
             $http.post(url).then(function (response) {
                alert(String(response.data));
             });
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
