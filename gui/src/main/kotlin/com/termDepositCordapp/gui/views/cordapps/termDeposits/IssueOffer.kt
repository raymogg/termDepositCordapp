package com.termDepositCordapp.gui.views.cordapps.termDeposits


import com.termDepositCordapp.gui.views.stringConverter
import com.termDepositCordapp.gui.model.TermDepositsModel
import com.termDeposits.contract.KYC
import com.termDeposits.contract.TermDeposit
import com.termDeposits.contract.TermDepositOffer
import com.termDeposits.flow.TermDeposit.IssueOffer
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
import net.corda.core.crypto.SecureHash
import net.corda.core.flows.FlowException
import net.corda.core.identity.AnonymousParty
import net.corda.core.identity.Party
import net.corda.core.messaging.startFlow
import net.corda.finance.USD
import org.controlsfx.dialog.ExceptionDialog
import tornadofx.*
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Created by raymondm on 14/08/2017.
 */

class IssueOffer : Fragment() {
    override val root by fxml<DialogPane>()
    // Components
    private val interestLabel by fxid<Label>()
    val endDateLabel by fxid<Label>()
    val durationLabel by fxid<Label>()
    val toLabel by fxid<Label>()

    private val interestTextField by fxid<TextField>()
    val endDateTextField by fxid<TextField>()
    val durationTextField by fxid<TextField>()
    val toChoiceBox by fxid<ChoiceBox<Party>>()
    // Inject data
    private val parties by observableList(NetworkIdentityModel::parties)
    private val rpcProxy by observableValue(NodeMonitorModel::proxyObservable)
    private val myIdentity by observableValue(NetworkIdentityModel::myIdentity)
    private val notaries by observableList(NetworkIdentityModel::notaries)
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
                    val endDate: LocalDateTime = LocalDateTime.parse(endDateTextField.text+"T00:00:00")
                    val interest = interestTextField.text.toFloat()
                    val toParty = toChoiceBox.value
                    val duration = durationTextField.text.toInt()
                    val dateData = TermDepositOffer.offerDateData(endDate, duration);
                    val attachmentInputStream = File("C:\\Users\\raymondm\\Documents\\termDepositsCordapp\\kotlin-source\\src\\main\\resources\\Example_TD_Contract.zip").inputStream()
                    val attachmentHash = rpcProxy.value?.uploadAttachment(attachmentInputStream)
                    val earlyTerms = TermDepositOffer.earlyTerms(true)
                    rpcProxy.value?.startFlow(IssueOffer::Initiator, dateData, interest, rpcProxy.value?.nodeInfo()!!.legalIdentities.first(),
                            toParty, attachmentHash!!, earlyTerms)
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



        interestLabel.text = "Interest: "
        toLabel.text = "To: "
        durationLabel.text = "Duration: "
        endDateLabel.text = "End Date(YYYY-MM-DD): "

        toChoiceBox.apply {
            items = parties.map { it.legalIdentities.first() }.observable()
            converter = stringConverter {
                it.name.organisation
            }
        }


        // Enable execute button when form is valid.
        root.buttonTypes.add(executeButton)
        //Use correct for validation
        //Add validation based on transactionType
        //root.lookupButton(executeButton).disableProperty().bind(formValidCondition.not())


    }
}

