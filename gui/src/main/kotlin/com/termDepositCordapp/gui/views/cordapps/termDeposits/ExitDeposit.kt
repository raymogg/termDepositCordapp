package com.termDepositCordapp.gui.views.cordapps.termDeposits

import com.termDepositCordapp.gui.views.stringConverter
import com.termDepositCordapp.gui.model.TermDepositsModel
import com.termDeposits.contract.KYC
import com.termDeposits.contract.TermDeposit
import com.termDeposits.flow.TermDeposit.KYCRetrievalFlowID
import com.termDeposits.flow.TermDeposit.RedeemTD
import javafx.beans.binding.Bindings
import javafx.beans.binding.BooleanBinding
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.Insets
import javafx.scene.control.*
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.stage.Window
import net.corda.client.jfx.model.*
import net.corda.client.jfx.utils.isNotNull
import net.corda.core.contracts.*
import net.corda.core.flows.FlowException
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import org.controlsfx.dialog.ExceptionDialog
import tornadofx.*
import java.time.Period

/**
 * Created by raymondm on 14/08/2017.
 */

class ExitDeposit : Fragment() {
    override val root by fxml<DialogPane>()
    // Components
    private val offerChoiceBox by fxid<ChoiceBox<StateAndRef<TermDeposit.State>>>()
    private val offerLabel by fxid<Label>()
    // Inject data
    private val offerStates by observableList(TermDepositsModel::maturedStates)
    // private val issuers by observableList(IssuerModel::issuers)
    private val rpcProxy by observableValue(NodeMonitorModel::proxyObservable)
    private val myIdentity by observableValue(NetworkIdentityModel::myIdentity)
    private val notaries by observableList(NetworkIdentityModel::notaries)
    private val cash by observableList(ContractStateModel::cash)
    private val executeButton = ButtonType("Execute", ButtonBar.ButtonData.APPLY)

    fun show(window: Window): Unit {
        newTransactionDialog(window).showAndWait().ifPresent { command: Unit ->
            val dialog = Alert(Alert.AlertType.INFORMATION).apply {
                headerText = null
                contentText = "Transaction Started."
                dialogPane.isDisable = false
                initOwner(window)
                show()
            }
            runAsync {

            }.ui {
                val type = when (command is Unit) {
                    true -> "Transaction Finished"
                    false -> "Transaction Started"
                }
                dialog.alertType = Alert.AlertType.INFORMATION
                dialog.dialogPane.isDisable = false
                dialog.dialogPane.content = gridpane {
                    padding = Insets(10.0, 40.0, 10.0, 20.0)
                    vgap = 10.0
                    hgap = 10.0
                    row { label(type) { font = Font.font(font.family, FontWeight.EXTRA_BOLD, font.size + 2) } }

                }
                dialog.dialogPane.scene.window.sizeToScene()
            }.setOnFailed {
                val ex = it.source.exception
                when (ex) {
                    is FlowException -> {
                        dialog.alertType = Alert.AlertType.ERROR
                        dialog.contentText = ex.message
                    }
                    else -> {
                        dialog.close()
                        ExceptionDialog(ex).apply { initOwner(window) }.showAndWait()
                    }
                }
            }
        }
    }


    private fun newTransactionDialog(window: Window) = Dialog<Unit>().apply {
        dialogPane = root
        initOwner(window)
        setResultConverter {
            when (it) {
                executeButton -> {
                    val linearID = offerChoiceBox.value.state.data.clientIdentifier
                    val kycData = rpcProxy.value?.startFlow(::KYCRetrievalFlowID, linearID)!!.returnValue.getOrThrow().first()
                    val kycNameData = KYC.KYCNameData(kycData.state.data.firstName, kycData.state.data.lastName, kycData.state.data.accountNum)
                    println("${kycNameData.firstName} ${kycNameData.lastName}")
                    val monthsDiff = Period.between(offerChoiceBox.value.state.data.startDate.toLocalDate(),offerChoiceBox.value.state.data.endDate.toLocalDate()).months
                    val yearsToMonthsDiff = Period.between(offerChoiceBox.value.state.data.startDate.toLocalDate(),offerChoiceBox.value.state.data.endDate.toLocalDate()).years * 12
                    val difference = monthsDiff + yearsToMonthsDiff
                    val dateData = TermDeposit.DateData(offerChoiceBox.value.state.data.startDate,difference)
                    rpcProxy.value?.startFlow(RedeemTD::RedemptionInitiator, dateData,
                            offerChoiceBox.value.state.data.interestPercent, offerChoiceBox.value.state.data.institute,  offerChoiceBox.value.state.data.depositAmount, kycNameData)
                }
                else -> null
            }
        }
    }

    init {
        // Disable everything when not connected to node.
        val notariesNotNullBinding = Bindings.createBooleanBinding({ notaries.isNotEmpty() }, arrayOf(notaries))
        val enableProperty = myIdentity.isNotNull().and(rpcProxy.isNotNull()).and(notariesNotNullBinding)
        root.disableProperty().bind(enableProperty.not())

        //Display the current offer states
        offerLabel.text = "Offers"
        // Loan Selection
        offerChoiceBox.apply {
            items = offerStates
            converter = stringConverter { "Issuing institute: " + it.state.data.institute.toString() +
                    "\n Interest: "+ it.state.data.interestPercent+"%" +
                    "\n Deposited Amount " + it.state.data.depositAmount.toString() +
                    "\n End Date " + it.state.data.endDate.toString()}
        }

        // Validate inputs.
        val formValidCondition = arrayOf(
                //myIdentity.isNotNull(),
                offerChoiceBox.valueProperty().isNotNull

        ).reduce(BooleanBinding::and)

        // Enable execute button when form is valid.
        root.buttonTypes.add(executeButton)
        //Use correct for validation
        //Add validation based on transactionType
        root.lookupButton(executeButton).disableProperty().bind(formValidCondition.not())


    }
}

