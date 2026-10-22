package chipyard.fpga.fletcherlake

import chisel3._
import chisel3.util._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.prci._
import freechips.rocketchip.subsystem.SystemBusKey
import freechips.rocketchip.tilelink._
import org.chipsalliance.cde.config.Parameters

import sifive.fpgashells.clocks._
import sifive.fpgashells.shell._
import sifive.fpgashells.shell.xilinx._

import chipyard._
import chipyard.harness._

class FletcherLakeHarness(override implicit val p: Parameters) extends FletcherLakeShell {
  def dp = designParameters

  val clockOverlay = dp(ClockInputOverlayKey).map(_.place(ClockInputDesignInput())).head
  val harnessSysPLL = dp(PLLFactoryKey)
  val harnessSysPLLNode = harnessSysPLL()
  val dutFreqMHz = (dp(SystemBusKey).dtsFrequency.get / (1000 * 1000)).toInt
  val dutClock = ClockSinkNode(freqMHz = dutFreqMHz)
  println(s"Fletcher Lake FPGA Base Clock Freq: $dutFreqMHz MHz")
  val dutWrangler = LazyModule(new ResetWrangler())
  val dutGroup = ClockGroup()
  dutClock := dutWrangler.node := dutGroup := harnessSysPLLNode

  harnessSysPLLNode := clockOverlay.overlayOutput.node

  // Initial Fletcher Lake bring-up reuses the Bora Lake DDR3 subsystem. This
  // cast should change when Fletcher Lake gets a distinct MIG.
  val ddrOverlay = dp(DDROverlayKey).head
    .place(DDRDesignInput(dp(ExtTLMem).get.master.base, dutWrangler.node, harnessSysPLLNode))
    .asInstanceOf[DDRBoraLakePlacedOverlay]
  val ddrClient = TLClientNode(Seq(TLMasterPortParameters.v1(Seq(TLMasterParameters.v1(
    name = "chip_ddr",
    sourceId = IdRange(0, 1 << dp(ExtTLMem).get.master.idBits)
  )))))
  val ddrBlockDuringReset = LazyModule(new TLBlockDuringReset(4))
  ddrOverlay.overlayOutput.ddr := ddrBlockDuringReset.node := ddrClient

  val ledOverlays = dp(LEDOverlayKey).map(_.place(LEDDesignInput()))
  val allLeds = ledOverlays.map(_.overlayOutput.led)
  val statusLeds = allLeds.take(6)
  println(s"Status LEDs: $statusLeds, all LEDs: $allLeds")

  override lazy val module = new HarnessLikeImpl

  class HarnessLikeImpl extends Impl with HasHarnessInstantiators {
    allLeds.foreach(_ := DontCare)
    // resetPin is normalized by FletcherLakeShell to active-high asserted.
    clockOverlay.overlayOutput.node.out(0)._1.reset := resetPin

    val clk50MHz = clockOverlay.overlayOutput.node.out.head._1.clock

    // Blink the status LEDs for a basic programming/clock sanity check.
    withClockAndReset(clk50MHz, dutClock.in.head._1.reset) {
      val period = (BigInt(50) << 20) / 2
      val counter = RegInit(0.U(log2Ceil(period).W))
      val pulse = RegInit(0.U(1.W))
      counter := Mux(counter === (period - 1).U, 0.U, counter + 1.U)
      when(counter === 0.U) {
        pulse := ~pulse
      }

      statusLeds(0) := pulse
      statusLeds(1) := resetPin
    }

    statusLeds(2) := 1.U
    // FPGA_LED3 (M22): DDR3 controller finished calibration. Until it does,
    // ddrBlockDuringReset holds every memory access, so TSI reads of DDR hang.
    statusLeds(3) := ddrOverlay.mig.module.io.port.init_calib_complete
    statusLeds(4) := 1.U
    statusLeds(5) := 1.U

    harnessSysPLL.plls.foreach(_._1.getReset.get := pllReset)

    def referenceClockFreqMHz = dutFreqMHz
    def referenceClock = dutClock.in.head._1.clock
    def referenceReset = dutClock.in.head._1.reset
    def success = { require(false, "Unused"); false.B }

    childClock := harnessBinderClock
    childReset := harnessBinderReset

    ddrOverlay.mig.module.clock := harnessBinderClock
    ddrOverlay.mig.module.reset := harnessBinderReset
    ddrBlockDuringReset.module.clock := harnessBinderClock
    ddrBlockDuringReset.module.reset :=
      harnessBinderReset.asBool || !ddrOverlay.mig.module.io.port.init_calib_complete

    instantiateChipTops()
  }
}
