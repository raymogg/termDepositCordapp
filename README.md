# GBST Term Deposits Cordapp

The following documents the API and setup guide for the Term Deposits Cordapp - Developed on Corda V3.

## Setup Guide
### Requirements
* IntelliJ or Eclipse Java IDE.
* Java 1.8+
* Kotlin 1.1.60
### Setup
* Download/Clone the repository found at https://github.com/raymogg/termDepositCordapp.
* Open the project in your IDE. Gradle will then import all dependencies – this may take 5 – 10 minutes.
* Once the project is loaded, navigate to kotlin-source/src/test/com.example/NodeDriver. Here you will be able to run the simulation which will start up 5 Corda Nodes – AMM, ClientA, ClientB, BankA, BankB. The simulation will then run some example transactions – issuing cash to the parties, sending out term deposit offers, starting a term deposit, and finally activating that term deposit.
* You can now interact with the nodes.
### Interacting with the Nodes
There are a few options in terms of interacting with the nodes – the modified Corda GUI, altering the simulation to call different methods provided, making API calls (using an app such as Postman), or through the provided Web UI (still in development). To run any of these, first make sure you have run the NodeDriver.kt file as stated above. The recommended method is using the CordaGUI.

#### Corda GUI (Corda Demo Bench)
A modified corda GUI is provided with the project under the gui folder. By running to gui/src/main/kotlin/Main.kt, a UI to interact with the node will be launched. To login, the following login details can be used – username: user1, password: test. For the port, enter the port corresponding to the node you wish to start. These can be found in the original corda output logs underneath the Webserver lines. The nodes name and corresponding port should be shown. The UI will then be launched and you may interact with the node.
#### Altering NodeDriver.kt simulation
When running the NodeDriver, you can add in some extra method calls to run different tests. All features of the term deposit. All methods can be found at the bottom of the NodeDriver.kt file, and some example calls of these have been commented out under the runSimulation function.
#### API Calls
When each node is activated,it starts up a webserver allowing you to interact with it via API calls. To do this, first see what port the webserver is started on – this will again be printed in the output of the NodeDriver.kt file, in a line stating “Webservers Started: ….”. It is recommended a API app such as postman be used. To find out what calls are available, see the API documentation below.
#### Web UI (Under Development)
Each node can also be accessed via browser using the provided webserver started with the node. Here a simple Web UI has been implemented which can be accessed by clicking on the TermDepositsWeb link on the main page. This feature is still under development, but currently each node will display its current term deposits, and can issue new term deposits by selecting an offer, customer and deposit amount.

## API
The following is the API for both Term Deposits and KYC Data. Note that for POST calls, the cordapp will always respond with the transaction ID if the call is successful.

### Term Deposits API
#### Get All Deposits (GET)
**/api/term_deposits/deposits**

Returns a JSON containing an array of term deposit states for every term deposit in the nodes vault. Each term deposit contains the following fields in its mapping:
* from: Party – the party which issued the term deposit (eg Commbank)
* to: Party – the party which opened the term deposit (Eg AMM)
* percent: Float – the interest percent of the term deposit
* startDate: LocalDateTime – the start date of the term deposit
* endDate: LocalDateTime – the end date of the term deposit
* client: LinearID – the ID of the client for which this deposit was opened for (eg a customer of AMM)
* amount: Amount<Currency> - the deposited amount
* internalState: String – the internal state of the term deposit (either pending, active, or terminated)
  
**Example Response**

{
  "states" : [ {
    "from" : "O=BankA, L=Munich, C=DE",
    "to" : "O=AMM, L=London, C=GB",
    "percent" : 2.65,
    "startDate" : "-999999999-01-01",
    "endDate" : "-999999998-01-01",
    "client" : {
      "externalId" : null,
      "id" : "4cf94919-673c-41ac-8d01-fa7aa7bef9c5"
    },
    "amount" : "300.00 USD",
    "internalState" : "Active"
  }, {
    "from" : "O=BankA, L=Munich, C=DE",
    "to" : "O=AMM, L=London, C=GB",
    "percent" : 2.55,
    "startDate" : "-999999999-01-01",
    "endDate" : "-999999999-07-01",
    "client" : {
      "externalId" : null,
      "id" : "451dc874-77d7-4969-8f3f-3778fe2ddc82"
    },
    "amount" : "5500.00 USD",
    "internalState" : "Pending"
  } ]
}

#### Get All Offers (GET)
**/api/term_deposits/offers**

Returns a JSON containing an array of term deposit offer states for every offer issued to this node. Each offer contains the following 
* validTill: LocalDateTime – the date for which this offer is running until.
* interest: Float – the interest percent for this offer
* duration: Int – the duration for which this deposit runs (in months)
* issuingInstitue: Party – the institute who issued this offer (eg Commbank)

**Example Response**

{
  "offers" : [ {
    "validTill" : "+999999999-12-31",
    "interest" : 3.1,
    "duration" : 18,
    "issuingInstitute" : "O=BankA, L=Munich, C=DE"
  }, {
    "validTill" : "+999999999-12-31",
    "interest" : 2.7,
    "duration" : 6,
    "issuingInstitute" : "O=BankB, L=Singapore, C=SG"
  }, {
    "validTill" : "+999999999-12-31",
    "interest" : 3.0,
    "duration" : 12,
    "issuingInstitute" : "O=BankB, L=Singapore, C=SG"
  }, {
    "validTill" : "+999999999-12-31",
    "interest" : 2.95,
    "duration" : 18,
    "issuingInstitute" : "O=BankB, L=Singapore, C=SG"
  }, {
    "validTill" : "+999999999-12-31",
    "interest" : 2.65,
    "duration" : 12,
    "issuingInstitute" : "O=BankA, L=Munich, C=DE"
  }, {
    "validTill" : "+999999999-12-31",
    "interest" : 2.55,
    "duration" : 6,
    "issuingInstitute" : "O=BankA, L=Munich, C=DE"
  } ]
}

#### Issue a Term Deposit (POST)
**/api/term_deposits/issue_td**

POST call to issue a term deposit from this node. Inputs such as offeringInstitute, interestPercent, duration, customer_fname, customer_lname and customer_anum must match a term deposit offer held by this node and some client information respectively. It is recommended the UI allow a user to select only available offers and clients when this post call is made – otherwise the Corda flow will fail. Note that when passing in the offering institute, Corda uses its own CordaX500 naming system. The string that must be passed in is the organisation name – the cordapp will then find the appropriate CordaX500 name corresponding to this organisation name. (See https://docs.corda.net/api/kotlin/corda/net.corda.core.identity/-corda-x500-name/index.html) Required inputs are:
* td_value: Int – the deposit amount for this term deposit.
* offering_institute: String – the institute who’s term deposit offer is being used to issue this deposit.
* interest_percent: Float – the interest percent for this term deposit.
* duration: Int – the duration of this term deposit.
* customer_fname: String – the first name of the customer for which this deposit is being started on behalf of.
* customer_lname: String - the first name of the customer for which this deposit is being started on behalf of.
* customer_anum: String – the account number of the customer for which this deposit is being started on behalf of.

**Example Call**

http://localhost:10010/api/term_deposits/issue_td?td_value=500&offering_institute=BankA&interest_percent=2.55&duration=6&customer_fname=Jane&customer_lname=Doe&customer_anum=9384

#### Activate Term Deposit (POST)
**/api/term_deposits/activate_td**

POST call to activate a term deposit – this call must be made from the issuing bank node, and is to be made once the off ledger confirmation of asset transfer has been confirmed (cash has arrived at the bank). All inputs here must correspond to a currently pending term deposit – it is recommended a front end UI provide the user with a choice of pending term deposits, then all the input terms are pulled from these pending term deposits. Note that when passing in the offering institute and client strings, Corda uses its own CordaX500 naming system. The string that must be passed in is the organisation name – the cordapp will then find the appropriate CordaX500 name corresponding to this organisation name. (See https://docs.corda.net/api/kotlin/corda/net.corda.core.identity/-corda-x500-name/index.html) Required inputs are:
* td_value: Int – the deposit amount for this term deposit.
* offering_institute: String – the institute who’s term deposit offer is being used to issue this deposit.
* interest_percent: Float – the interest percent for this term deposit.
* duration: Int – the duration of this term deposit.
* customer_fname: String – the first name of the customer for which this deposit is being started on behalf of.
* customer_lname: String - the first name of the customer for which this deposit is being started on behalf of.
* customer_anum: String – the account number of the customer for which this deposit is being started on behalf of.
* start_date: String – the date for which this term deposit started – must be in the format “YYYY-MM-DD”
* client: String – the client who is opening the term deposit (eg AMM)

**Example Call**

http://localhost:10019/api/term_deposits/activate_td?td_value=500&offering_institute=BankA&interest_percent=2.55&duration=6&customer_fname=Jane&customer_lname=Doe&customer_anum=9384&start_date=2007-12-03T10:15:30&client=AMM

#### Redeem a Term Deposit (POST)
**/api/term_deposits/redeem_td**

Post call to redeem a term deposit. All paramaters passed in must belong to a currently active term deposit which has passed its end date. It is recommended the UI provide users with a choice of active term deposits which have passed their end date, then pass in the paramaters from this. The required inputs are:
* td_value: Int – the deposit amount for this term deposit.
* offering_institute: String – the institute who’s term deposit offer is being used to issue this deposit.
* interest_percent: Float – the interest percent for this term deposit.
* duration: Int – the duration of this term deposit.
* customer_fname: String – the first name of the customer for which this deposit is being started on behalf of.
* customer_lname: String - the first name of the customer for which this deposit is being started on behalf of.
* customer_anum: String – the account number of the customer for which this deposit is being started on behalf of.
* start_date: String – the date for which this term deposit started – must be in the format “YYYY-MM-DD”

**Example Call**

http://localhost:10010/api/term_deposits/redeem_td?td_value=300&offering_institute=BankA&interest_percent=2.65&duration=12&customer_fname=Bob&customer_lname=Smith&customer_anum=1234&start_date=2007-12-03T10:15:30

#### Rollover a Term Deposit (POST)
**/api/term_deposits/rollover_td**

POST call to rollover a term deposit. As before, input paramaters td_value, offering_institute, interest_percent, duration, customer_fname, customer_lname, customer_anum and start_date must correspond to a currently active term deposit that has passed its end date. The paramaters new_interest, new_intstitute and new_duration must correspond to a term deposit offer held by the node. The required inputs are
* td_value: Int – the deposit amount for this term deposit.
* offering_institute: String – the institute who’s term deposit offer is being used to issue this deposit.
* interest_percent: Float – the interest percent for this term deposit.
* duration: Int – the duration of this term deposit.
* customer_fname: String – the first name of the customer for which this deposit is being started on behalf of.
* customer_lname: String - the first name of the customer for which this deposit is being started on behalf of.
* customer_anum: String – the account number of the customer for which this deposit is being started on behalf of.
* start_date: String – the date for which this term deposit started – must be in the format “YYYY-MM-DD”
* new_interest: Float – the interest for the new term deposit being started
* new_institute: String – the institute offering the new term deposit that is being started.
* new_duration: Int – the duration (in months) of the new term deposit.
* with_interest: Boolean – a Boolean indicating if the interest from the original term deposit should be rolled over into the new deposit (true) or if it should be returned to the client (false).

**Example Call**

http://localhost:10010/api/term_deposits/rollover_td?td_value=300&offering_institute=BankA&interest_percent=2.65&duration=12&customer_fname=Bob&customer_lname=Smith&customer_anum=1234&start_date=2007-12-03T10:15:30&new_interest=2.65&new_institute=BankA&new_duration=6&with_interest=true

### KYC API
#### Get Client KYC data (GET)
**/api/term_deposits/kyc**

Gets all KYC data known by this node. Returns a JSON containing an array of KYC states. Each KYC data state contains the following fields:
* firstName: String – the first name of the customer
* lastName: String – the last name of the customer
* accountNum: String – the account number of the client
* uniqueIdentifier: UniqueIdentifier – the unique ID given to the customers corda state. No two clients have the same unique identifier as this is used within corda to pass client data around.

**Example Response**

{
  "kyc" : [ {
    "firstName" : "Bob",
    "lastName" : "Smith",
    "accountNum" : "1234",
    "uniqueIdentifier" : {
      "externalId" : null,
      "id" : "9128180d-cd67-4863-b4cc-f69036ee2624"
    }
  }, {
    "firstName" : "Jane",
    "lastName" : "Doe",
    "accountNum" : "9384",
    "uniqueIdentifier" : {
      "externalId" : null,
      "id" : "09715d4b-4d4b-450b-9155-cf472c6fb4e9"
    }
  } ]
}

#### Create KYC Data (POST)
**/api/term_deposits/create_kyc**

POST call to create new KYC data. A unique identifier will be provided for the state by Corda. Required inputs are:
* first_name: String – the first name of the customer
* last_name: String – the last name of the customer
* account_num: String – the customers account number (either bank account or account number with AMM).

**Example Call**

http://localhost:10010/api/term_deposits/create_kyc?first_name=Ray&last_name=Mogg&account_num=12345

#### Update KYC Data (POST)
**/api/term_deposits/update_kyc**

POST call to update existing KYC data. Note that for first name, last name and account number, if nothing is supplied to the call, this will default to not changing that field. Required inputs are:
* customer_id: String – the uniqueIdentifier for the state being updated
* new_fname: String – the new first name for this customer.
* new_lname: String – the new last name for this customer.
* new_anum: String – the new account number for this customer.

**Example Call**

http://localhost:10010/api/term_deposits/update_kyc?customer_id=69557e6b-1721-439f-bbbe-8f6c91023e51&new_anum=1234567

