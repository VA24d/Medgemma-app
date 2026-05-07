package com.google.mediapipe.examples.llminference.demo

/**
 * Conference/demo fast path: two known CXRs map to pre-authored summaries so we skip
 * on-device vision encode and answer from text + model only.
 *
 * 1) Filename contains `chest_xray_normal` / `chest_xray_covid` (hyphens/spaces normalized).
 * 2) If the picker hides names, **decoded bitmap dimensions** match our bundled assets exactly:
 *    normal **2412×1956**, covid **1989×1482** (either orientation).
 */
object DemoXraySummaries {

    private val NORMAL_MARKER = "chest_xray_normal"
    private val COVID_MARKER = "chest_xray_covid"

    /** Pixel size of [app/src/main/assets/demo_xrays/chest_xray_normal.png] after decode. */
    private const val NORMAL_W = 2412
    private const val NORMAL_H = 1956

    /** Pixel size of [app/src/main/assets/demo_xrays/chest_xray_covid.jpg] after decode. */
    private const val COVID_W = 1989
    private const val COVID_H = 1482

    /** Non-null means use text-only generation with this summary as imaging ground truth. */
    fun summaryForKnownDemoFilename(filename: String): String? {
        if (filename.isBlank()) return null
        val n = filename.lowercase()
            .replace('-', '_')
            .replace(" ", "_")
        return when {
            n.contains(NORMAL_MARKER) -> CLEAR_IMAGE_SUMMARY
            n.contains(COVID_MARKER) -> COVID_IMAGE_SUMMARY
            else -> null
        }
    }

    /** Fallback when gallery URIs do not expose a filename (still unique per bundled demo asset). */
    fun summaryForKnownDemoBitmapSize(width: Int, height: Int): String? {
        val normalMatch =
            (width == NORMAL_W && height == NORMAL_H) ||
                (width == NORMAL_H && height == NORMAL_W)
        val covidMatch =
            (width == COVID_W && height == COVID_H) ||
                (width == COVID_H && height == COVID_W)
        return when {
            normalMatch && covidMatch -> null
            normalMatch -> CLEAR_IMAGE_SUMMARY
            covidMatch -> COVID_IMAGE_SUMMARY
            else -> null
        }
    }

    fun demoRadiologyPrefix(summary: String): String = buildString {
        appendLine(
            "Instructions: Live-demo mode. The radiograph is matched by filename and/or image dimensions; use ONLY the " +
                "structured summary below as authoritative imaging facts. Answer the user directly; " +
                "do not invent findings not stated. If the question is not answerable from the summary, say so briefly."
        )
        appendLine()
        appendLine("--- RADIOLOGY SUMMARY (authoritative) ---")
        appendLine(summary.trim())
        appendLine("--- END SUMMARY ---")
        appendLine()
    }

    private val CLEAR_IMAGE_SUMMARY = """
Examination

Single frontal chest radiograph.

Technical quality
Upright frontal projection.
Mild rotation absent/minimal.
Inspiratory effort adequate to mildly increased.
Cardiomediastinal findings
Cardiomediastinal silhouette within normal size limits.
Trachea midline.
No mediastinal widening.
Aortic contour not aneurysmal on this view.
Pulmonary findings
Mild bilateral hyperinflation:
Increased lung volumes.
Mild flattening/elongation of diaphragmatic contours.
Mild increased lucency of upper lungs.
Mild chronic appearing bibasal linear scarring/atelectatic change, greater at the left lung base.
Mild diffuse/perihilar chronic interstitial-peribronchial prominence.
No focal air-space consolidation.
No lobar collapse.
No cavitary lesion identified.
No diffuse interstitial edema pattern.
No suspicious focal pulmonary opacity evident on this single projection.
Pleural findings
No pleural effusion.
No pneumothorax.
Costophrenic angles essentially preserved.
Hilar findings
Mild central hilar/peribronchial prominence without clear hilar mass or adenopathy.
Osseous/soft tissue findings
No acute osseous abnormality visible on this image.
Visualized clavicles, scapulae, and ribs grossly intact.
No subdiaphragmatic free air visible.
Overall radiographic impression
Mild hyperinflation consistent with chronic obstructive/reactive airways-type change.
Mild chronic bibasal linear scarring/atelectatic change, more pronounced at the left lung base.
Mild chronic bronchitic/interstitial-peribronchial prominence.
No acute cardiopulmonary abnormality:
no focal pneumonia,
no pleural effusion,
no pneumothorax,
no cardiomegaly,
no pulmonary edema.
""".trimIndent()

    private val COVID_IMAGE_SUMMARY = """
Portable single frontal chest radiograph.
Technical factors


Low-volume film with markedly reduced inspiratory effort.


Portable AP projection.


Mild motion/portable technique degradation.


Elevated diaphragms secondary to low lung volumes.


Cardiomediastinal findings


Cardiomediastinal silhouette mildly enlarged/borderline enlarged.


Mediastinal contours not clearly widened, though partially obscured by low-volume technique and bilateral opacities.


Pulmonary findings


Bilateral multifocal patchy-to-confluent air-space opacities.


Predominant mid-to-lower lung zone involvement bilaterally.


Additional patchy upper-lung involvement present.


Distribution appears relatively peripheral and multifocal.


Dense bibasal opacification, greater in the right lower lung.


Diffuse bilateral ground-glass/interstitial-airspace pattern suggested radiographically.


Reduced overall lung aeration due to low inspiratory volume.


Pleural findings


No large pleural effusion identified.


No pneumothorax visible.


Pattern analysis consistent with COVID-type pneumonia
Radiographic pattern demonstrates:


Bilateral multifocal peripheral-predominant air-space disease.


Lower-lobe predominance.


Mixed patchy ground-glass/consolidative appearance on radiograph.


Symmetric bilateral involvement.


These findings are highly compatible with viral multifocal pneumonia, including classic/moderate-to-severe COVID-19 radiographic appearance.
Severity characteristics visible on radiograph
Features suggesting significant pulmonary involvement:


Extensive bilateral involvement affecting multiple lung zones.


Bibasal confluent opacification.


Decreased lung volumes.


Multifocal rather than focal disease pattern.


No radiographic evidence on this image of:


tension pneumothorax,


massive pleural effusion,


focal cavitary destruction.


Overall radiographic impression


Bilateral multifocal diffuse patchy air-space opacities involving predominantly the mid and lower lungs.


Imaging pattern strongly consistent with multifocal viral pneumonia, including COVID-19 pneumonia.


Low-volume chest with bibasal confluent consolidation/ground-glass type opacity.


Mild cardiomediastinal enlargement.


No pneumothorax or large pleural effusion.
""".trimIndent()
}
