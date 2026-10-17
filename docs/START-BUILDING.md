# Start building Portiva

**Yes: start with the camera development stage.** It gives you hardware to assemble and test without first buying the rack, motors and battery. This is a useful experiment, not a promise that the final trolley will work within ₹3,000.

Supplier pages checked on 16 October 2026:

| Buy for this stage | Qty | Listed price | Link |
|---|---:|---:|---|
| AI-Thinker ESP32-CAM with OV2640 and 4 MB PSRAM | 1 | ₹850 including GST | [KSP camera](https://kspelectronics.in/product/esp32-cam-wifi-bluetooth-development-board-ov2640/) |
| ESP32-CAM-MB Type-C CH340C programmer | 1 | ₹161 including GST | [FlyRobo programmer](https://www.flyrobo.in/esp32-cam-mb-type-c-serial-port-burner-module-with-ch340c) |
| USB data cable that fits that programmer | 1 | Reuse if available; otherwise price at checkout | Must carry data, not only charging |

**Board and programmer subtotal: ₹1,011.** Shipping, cable and any additional power supply are outside this subtotal. Delivery and final charges for your location are not verified. Both pages currently list stock. Before payment, match the actual programmer socket/header arrangement to the AI-Thinker board and confirm an OV2640 with 4 MB PSRAM. Avoid a seller substituting another camera or pin map.

For this bench, a suitable computer USB port and matching programmer can supply power. Check the board/programmer documentation and stable regulated 5 V capability. The camera vendor specifies at least 500 mA. Brownouts are a test failure. This temporary bench power arrangement is not the final trolley battery. No power bank is proposed.

1. Start with the software demo, with no purchase.
2. Get the board, matching programmer and data cable.
3. Create private credentials with `python implementation/configure_camera.py`. Existing files are preserved; deliberate replacement needs `--force`.
4. Build and upload the camera sketch after matching its pin map. Keep motor power disconnected. See [setup](SOFTWARE.md).
5. Join its local Wi-Fi. Run `python portiva.py camera --url http://192.168.4.1 --frames 20`. The token is entered at a hidden prompt.
6. The test checks authentication, sensor identity, PSRAM and 20 decodable frames. It records a real result only when connected to real hardware.
7. When a compatible Android phone is available, run its offline detector benchmark and then the live camera preview. A physical phone pass is not assumed.

The next purchasing decision depends on real image quality, phone performance and scene suitability. The full moving trolley BOM remains incomplete: the rack/base fit, 5 kg drive, battery/charger, stop circuit and total delivered cost still need qualification. The older full-basket CSVs are research records and are not shopping instructions.

If ₹3,000 is a strict total that you cannot risk spending on experiments, run the free phone/software tests before paying for this stage. No current simulation proves the complete ₹3,000 result.
