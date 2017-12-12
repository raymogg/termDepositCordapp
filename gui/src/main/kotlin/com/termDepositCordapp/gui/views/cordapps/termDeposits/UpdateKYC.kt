package com.termDepositCordapp.gui.views.cordapps.termDeposits

import com.termDepositCordapp.gui.model.TermDepositsModel
import com.termDepositCordapp.gui.views.stringConverter
import com.termDeposits.contract.KYC
import com.termDeposits.flow.TermDeposit.CreateKYC
import com.termDeposits.flow.TermDeposit.UpdateKYC
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.geometry.Insets
import javafx.scene.control.*
import javafx.scene.text.Font
import javafx.scene.text.FontWeight
import javafx.stage.Window
import net.corda.client.jfx.model.*
import net.corda.client.jfx.utils.isNotNull
import net.corda.core.contracts.StateAndRef
import net.corda.core.flows.FlowException
import net.corda.core.messaging.startFlow
import org.controlsfx.dialog.ExceptionDialog
import tornadofx.Fragment
import tornadofx.gridpane
import tornadofx.label
import tornadofx.row

class UpdateKYC : Fragment() {
    override val root by fxml<DialogPane>()
    // Components
    private val newAccountNumTextField by fxid<TextField>()
    private val kycChoiceBox by fxid<ChoiceBox<StateAndRef<KYC.State>>>()

    private val newAccountNumLabel by fxid<Label>()
    private val kycLabel by fxid<Label>()

    private val withInterestLabel by fxid<Label>()
    private val issueRef = SimpleObjectProperty<Byte>()
    // Inject data
    private val parties by observableList(NetworkIdentityModel::parties)
    private val kycStates by observableList(TermDepositsModel::KYCStates)
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
                    //Update KYC
                    rpcProxy.value?.startFlow(UpdateKYC::Updator, kycChoiceBox.value.state.data.linearId, newAccountNumTextField.text)
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

        //Load in the states that can be attempted to rollover
        kycLabel.text = "KYC Data: "
        newAccountNumLabel.text = "New Account Number: "

        kycChoiceBox.apply {
            items = kycStates
            converter = stringConverter {
                "Name: ${it.state.data.firstName} ${it.state.data.lastName}" +
                        "\n Client ID: ${it.state.data.linearId}"
            }
        }

        // Validate inputs.
//        val formValidCondition = arrayOf(firstNameTextField.text.isNullOrBlank().not()
//
//        ).reduce(BooleanBinding::and)

        // Enable execute button when form is valid.
        root.buttonTypes.add(executeButton)
        //Use correct for validation
        //Add validation based on transactionType
        //root.lookupButton(executeButton).disableProperty().bind(formValidCondition.not())


    }
}

