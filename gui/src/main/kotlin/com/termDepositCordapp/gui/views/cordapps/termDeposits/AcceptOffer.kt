package com.termDepositCordapp.gui.views.cordapps.termDeposits

import com.termDepositCordapp.gui.views.stringConverter
import com.termDepositCordapp.gui.model.TermDepositsModel
import com.termDeposits.contract.TermDepositOffer
import com.termDeposits.flow.TermDeposit.IssueTD
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
import net.corda.finance.USD
import org.controlsfx.dialog.ExceptionDialog
import tornadofx.*
import java.time.LocalDateTime

/**
 * Created by raymondm on 14/08/2017.
 */

class AcceptOffer : Fragment() {
    override val root by fxml<DialogPane>()
    // Components
    private val offerChoiceBox by fxid<ChoiceBox<StateAndRef<TermDepositOffer.State>>>()
    private val offerLabel by fxid<Label>()
    private val depositLabel by fxid<Label>()
    private val depositTextField by fxid<TextField>()
    // Inject data
    private val parties by observableList(NetworkIdentityModel::parties)
    private val offerStates by observableList(TermDepositsModel::offerStates)
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
                    //TODO Execute accept offer
                    rpcProxy.value?.startFlow(IssueTD::Initiator, LocalDateTime.MIN, LocalDateTime.MAX, offerChoiceBox.value.state.data.interestPercent,
                            offerChoiceBox.value.state.data.institue,  Amount(depositTextField.text.toLong()*100, USD))
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
        //refresh loan states





        offerLabel.text = "Offers"
        depositLabel.text = "Deposit Amount"
        // Loan Selection
        offerChoiceBox.apply {
            items = offerStates
            converter = stringConverter { "Issuing Institue: " + it.state.data.institue.toString() +
                 "\n Interest: "+ it.state.data.interestPercent+"%" +
                        "\n Valid till: " + it.state.data.validTill.toString()}
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

