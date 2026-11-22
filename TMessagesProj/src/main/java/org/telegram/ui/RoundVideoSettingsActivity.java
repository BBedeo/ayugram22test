package org.telegram.ui;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Build;
import android.util.Range;
import android.util.Size;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.utils.settings.SharedSettings;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeBind;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.InstantCameraViewBase;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.utils.recorder.round.RoundVideoSession;

/** Configures the Camera2 round-video implementation. */
public class RoundVideoSettingsActivity extends BaseFragment {
    private static final int TYPE_HEADER = 0;
    private static final int TYPE_CHECK = 1;
    private static final int TYPE_VALUE = 2;
    private static final int TYPE_INFO = 3;

    private static final int ROW_GENERAL_HEADER = 0;
    private static final int ROW_ENABLED = 1;
    private static final int ROW_OUTPUT_RESOLUTION = 2;
    private static final int ROW_CAMERA_RESOLUTION = 3;
    private static final int ROW_FRAME_RATE = 4;
    private static final int ROW_SHOW_FPS = 5;
    private static final int ROW_BITRATE = 6;
    private static final int ROW_GENERAL_INFO = 7;
    private static final int ROW_DIAGNOSTICS = 8;
    private static final int ROW_COMPOSITION_HEADER = 9;
    private static final int ROW_COMPOSITION = 10;
    private static final int ROW_COMPOSITION_INFO = 11;
    private static final int ROW_COUNT = BuildVars.DEBUG_PRIVATE_VERSION ? 12 : 9;

    private static final int[] BITRATES = {
            750_000,
            1_000_000,
            1_200_000,
            2_000_000,
            3_000_000,
            4_000_000,
            6_000_000,
            8_000_000,
            10_000_000
    };

    private RecyclerListView listView;
    private ListAdapter adapter;

    @Override
    protected boolean useSimpleActionBar() {
        return true;
    }

    @Override
    public View createView(Context context) {
        setHasOwnBackground(true);
        simpleActionBar.setBackButtonImage(R.drawable.ic_ab_back);
        simpleActionBar.setTitle(LocaleController.getString(R.string.RoundVideoSettings));
        simpleActionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        fragmentView = frameLayout;

        listView = new RecyclerListView(context);
        listView.setSections();
        listView.setSectionsDrawBackground(true);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position) -> onRowClicked(position));
        frameLayout.addView(listView, LayoutHelper.createFrame(
                LayoutHelper.MATCH_PARENT,
                LayoutHelper.MATCH_PARENT
        ));
        return fragmentView;
    }

    @Override
    public boolean isSupportEdgeToEdge() {
        return true;
    }

    @Nullable
    @Override
    protected RecyclerListView getListViewForSimpleGlass() {
        return listView;
    }

    private void onRowClicked(int position) {
        if (position == ROW_ENABLED) {
            SharedSettings.roundVideoCamera2Enabled.set(!InstantCameraViewBase.isRoundVideoRecorderV2(currentAccount));
            adapter.notifyDataSetChanged();
            return;
        }
        if (position == ROW_DIAGNOSTICS) {
            showCameraDiagnostics();
            return;
        }
        if (position == ROW_SHOW_FPS) {
            SharedSettings.roundVideoShowFps.set(!SharedSettings.roundVideoShowFps.get());
            adapter.notifyItemChanged(position);
            return;
        }
        if (!InstantCameraViewBase.isRoundVideoRecorderV2(currentAccount)) {
            return;
        }
        if (position == ROW_OUTPUT_RESOLUTION) {
            final RoundVideoSession.OutputResolution[] resolutions = {
                    RoundVideoSession.OutputResolution.P1080,
                    RoundVideoSession.OutputResolution.P720,
                    RoundVideoSession.OutputResolution.P640,
                    RoundVideoSession.OutputResolution.P480,
                    RoundVideoSession.OutputResolution.P360
            };
            showChoice(
                    R.string.RoundVideoOutputResolution,
                    new CharSequence[]{"1080p", "720p", "640p", "480p", "360p"},
                    index -> SharedSettings.roundVideoOutputResolution.set(resolutions[index])
            );
        } else if (position == ROW_CAMERA_RESOLUTION) {
            showChoice(
                    R.string.RoundVideoCameraResolution,
                    new CharSequence[]{
                            LocaleController.getString(R.string.RoundVideoCameraResolutionHigh),
                            LocaleController.getString(R.string.RoundVideoCameraResolutionMedium),
                            LocaleController.getString(R.string.RoundVideoCameraResolutionLow)
                    },
                    index -> SharedSettings.roundVideoCameraResolution.set(
                            RoundVideoSession.CameraResolution.values()[index]
                    )
            );
        } else if (position == ROW_FRAME_RATE) {
            final RoundVideoSession.FrameRate[] frameRates = {
                    RoundVideoSession.FrameRate.FPS_30,
                    RoundVideoSession.FrameRate.FPS_60,
                    RoundVideoSession.FrameRate.FPS_120,
                    RoundVideoSession.FrameRate.FPS_240,
                    RoundVideoSession.FrameRate.FPS_MAX
            };
            showChoice(
                    R.string.RoundVideoFrameRate,
                    new CharSequence[]{"30 FPS", "60 FPS", "120 FPS", "240 FPS", "Max (Auto)"},
                    index -> {
                        SharedSettings.roundVideoFrameRate.set(frameRates[index]);
                        app.exteraless.chats.ChatsConfig.extendedFramesPerSecond.setConfigBool(index != 0);
                    }
            );
        } else if (position == ROW_SHOW_FPS) {
            SharedSettings.roundVideoShowFps.set(!SharedSettings.roundVideoShowFps.get());
            adapter.notifyItemChanged(position);
        } else if (position == ROW_BITRATE) {
            CharSequence[] labels = new CharSequence[BITRATES.length];
            for (int i = 0; i < BITRATES.length; i++) {
                labels[i] = formatBitrate(BITRATES[i]);
            }
            showChoice(
                    R.string.RoundVideoBitrate,
                    labels,
                    index -> SharedSettings.roundVideoVideoBitrate.set(BITRATES[index])
            );
        } else if (position == ROW_COMPOSITION) {
            SharedSettings.roundVideoComposition.set(!SharedSettings.roundVideoComposition.get());
            adapter.notifyItemChanged(position);
        }
    }

    private void showCameraDiagnostics() {
        StringBuilder text = new StringBuilder();
        boolean found = false;
        try {
            CameraManager manager = (CameraManager) ApplicationLoader.applicationContext.getSystemService(Context.CAMERA_SERVICE);
            for (String id : manager.getCameraIdList()) {
                try {
                    CameraCharacteristics characteristics = manager.getCameraCharacteristics(id);
                    Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                    if (facing == null || (facing != CameraCharacteristics.LENS_FACING_FRONT && facing != CameraCharacteristics.LENS_FACING_BACK)) {
                        continue;
                    }
                    found = true;
                    text.append(facing == CameraCharacteristics.LENS_FACING_FRONT ? "Front" : "Back")
                            .append(" camera (id=").append(id).append("):\n");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        java.util.Set<String> physicalIds = characteristics.getPhysicalCameraIds();
                        if (physicalIds != null && !physicalIds.isEmpty()) {
                            text.append("Physical IDs: ").append(physicalIds).append('\n');
                        }
                    }
                    float[] focals = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS);
                    if (focals != null && focals.length > 0) {
                        text.append("Focal mm: ").append(java.util.Arrays.toString(focals)).append('\n');
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Range<Float> zoomRange = characteristics.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
                        if (zoomRange != null) {
                            text.append("Zoom range: [").append(zoomRange.getLower())
                                    .append(", ").append(zoomRange.getUpper()).append("]\n");
                        }
                    }
                    Range<Integer>[] fpsRanges = characteristics.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);
                    int maxUpper = 0;
                    StreamConfigurationMap streamMap =
                            characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                    int sensorMaxFps = 0;
                    java.util.ArrayList<String> fastSizes = new java.util.ArrayList<>();
                    if (streamMap != null) {
                        Size[] texSizes = streamMap.getOutputSizes(SurfaceTexture.class);
                        if (texSizes != null) {
                            for (Size size : texSizes) {
                                long dur = streamMap.getOutputMinFrameDuration(SurfaceTexture.class, size);
                                if (dur > 0L) {
                                    int fps = (int) Math.round(1_000_000_000.0 / dur);
                                    sensorMaxFps = Math.max(sensorMaxFps, fps);
                                    if (dur <= 17_000_000L) {
                                        fastSizes.add(size.getWidth() + "x" + size.getHeight() + "@" + fps + "fps");
                                    }
                                }
                            }
                        }
                    }
                    if (fpsRanges != null) {
                        text.append("AE FPS (API): ");
                        for (Range<Integer> range : fpsRanges) {
                            text.append('[').append(range.getLower()).append(',').append(range.getUpper()).append("] ");
                            maxUpper = Math.max(maxUpper, range.getUpper());
                        }
                        if (sensorMaxFps > maxUpper) {
                            text.append("(OEM hides ").append(sensorMaxFps).append("+)");
                        }
                        text.append('\n');
                    }
                    if (sensorMaxFps > maxUpper || !fastSizes.isEmpty()) {
                        text.append("Sensor FPS (minDuration): ").append(sensorMaxFps).append(" fps");
                        if (!fastSizes.isEmpty()) {
                            text.append(" (").append(android.text.TextUtils.join(", ", fastSizes)).append(')');
                        }
                        text.append('\n');
                    }
                    Range<Integer>[] highSpeed = streamMap != null ? streamMap.getHighSpeedVideoFpsRanges() : null;
                    int maxHighSpeed = 0;
                    if (highSpeed != null && highSpeed.length > 0) {
                        text.append("High speed: ");
                        for (Range<Integer> range : highSpeed) {
                            text.append('[').append(range.getLower()).append(',').append(range.getUpper()).append("] ");
                            maxHighSpeed = Math.max(maxHighSpeed, range.getUpper());
                        }
                        Size[] hsSizes = streamMap.getHighSpeedVideoSizes();
                        if (hsSizes != null && hsSizes.length > 0) {
                            text.append("\nHS sizes: ").append(java.util.Arrays.toString(hsSizes));
                        }
                        text.append('\n');
                    }
                    int regularMax = Math.max(maxUpper, sensorMaxFps);
                    int effectiveMax = Math.max(regularMax, maxHighSpeed);
                    text.append("Max hardware FPS: ").append(effectiveMax)
                            .append(" fps (regular: ").append(regularMax).append(" fps)");
                    int[] ois = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION);
                    int[] eis = characteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
                    text.append("\nStab: OIS=")
                            .append(containsInt(ois, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON))
                            .append(", EIS=")
                            .append(containsInt(eis, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        text.append(", PreviewEIS=")
                                .append(containsInt(eis, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION));
                    }
                    text.append("\n\n");
                } catch (Exception ignored) {
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                java.util.Set<java.util.Set<String>> concurrent = manager.getConcurrentCameraIds();
                if (concurrent != null && !concurrent.isEmpty()) {
                    text.append("Concurrent sets: ").append(concurrent).append('\n');
                }
            }
        } catch (Throwable e) {
            text.append("Error: ").append(e);
        }
        if (!found) {
            text.append("Camera2 is not available on this device");
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(R.string.RoundVideoDiagnostics));
        builder.setMessage(text.toString());
        builder.setPositiveButton(LocaleController.getString(R.string.OK), null);
        showDialog(builder.create());
    }

    private void showChoice(int titleResId, CharSequence[] choices, ChoiceHandler handler) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getParentActivity());
        builder.setTitle(LocaleController.getString(titleResId));
        builder.setItems(choices, (dialog, index) -> {
            handler.onChoice(index);
            adapter.notifyDataSetChanged();
        });
        showDialog(builder.create());
    }

    private static String formatBitrate(int bitrate) {
        if (bitrate % 1_000_000 == 0) {
            return (bitrate / 1_000_000) + " Mbps";
        }
        if (bitrate > 1_000_000) {
            return String.format(java.util.Locale.US, "%.1f Mbps", bitrate / 1_000_000f);
        }
        return (bitrate / 1_000) + " kbps";
    }

    private interface ChoiceHandler {
        void onChoice(int index);
    }

    private static boolean containsInt(int[] values, int target) {
        if (values == null) return false;
        for (int v : values) {
            if (v == target) return true;
        }
        return false;
    }

    private static String frameRateLabel(RoundVideoSession.FrameRate rate) {
        if (rate == RoundVideoSession.FrameRate.FPS_240) {
            return "240 FPS";
        } else if (rate == RoundVideoSession.FrameRate.FPS_120) {
            return "120 FPS";
        } else if (rate == RoundVideoSession.FrameRate.FPS_MAX) {
            return "Max (Auto)";
        } else if (rate == RoundVideoSession.FrameRate.FPS_60 || SharedSettings.isRoundVideo60FpsRequested()) {
            return "60 FPS";
        }
        return "30 FPS";
    }

    private static String cameraResolutionLabel(RoundVideoSession.CameraResolution resolution) {
        if (resolution == RoundVideoSession.CameraResolution.HIGH) {
            return LocaleController.getString(R.string.RoundVideoCameraResolutionHigh);
        } else if (resolution == RoundVideoSession.CameraResolution.MEDIUM) {
            return LocaleController.getString(R.string.RoundVideoCameraResolutionMedium);
        }
        return LocaleController.getString(R.string.RoundVideoCameraResolutionLow);
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;

        private ListAdapter(Context context) {
            this.context = context;
        }

        @Override
        public int getItemCount() {
            return ROW_COUNT;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            if (position == ROW_ENABLED || position == ROW_DIAGNOSTICS || position == ROW_SHOW_FPS) {
                return true;
            }
            if (!InstantCameraViewBase.isRoundVideoRecorderV2(currentAccount)) {
                return false;
            }
            return position == ROW_OUTPUT_RESOLUTION
                    || position == ROW_CAMERA_RESOLUTION
                    || position == ROW_FRAME_RATE
                    || position == ROW_SHOW_FPS
                    || position == ROW_BITRATE
                    || position == ROW_COMPOSITION;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view;
            if (viewType == TYPE_HEADER) {
                view = new HeaderCell(context);
            } else if (viewType == TYPE_CHECK) {
                view = new TextCheckCell(context);
            } else if (viewType == TYPE_VALUE) {
                TextSettingsCell cell = new TextSettingsCell(context);
                cell.setCanDisable(true);
                view = cell;
            } else {
                view = new TextInfoPrivacyCell(context);
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
            ));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            boolean enabled = InstantCameraViewBase.isRoundVideoRecorderV2(currentAccount);
            if (holder.getItemViewType() == TYPE_HEADER) {
                HeaderCell cell = (HeaderCell) holder.itemView;
                cell.setText(position == ROW_GENERAL_HEADER
                        ? LocaleController.getString(R.string.RoundVideoGeneral)
                        : LocaleController.getString(R.string.RoundVideoComposition));
            } else if (holder.getItemViewType() == TYPE_CHECK) {
                TextCheckCell cell = (TextCheckCell) holder.itemView;
                cell.setEnabled(position == ROW_ENABLED || position == ROW_SHOW_FPS || enabled);
                if (position == ROW_ENABLED) {
                    cell.setTextAndCheck(
                            LocaleController.getString(R.string.RoundVideoUseNewRecorder),
                            enabled,
                            false
                    );
                } else if (position == ROW_SHOW_FPS) {
                    cell.setTextAndCheck(
                            LocaleController.getString(R.string.RoundVideoShowFps),
                            SharedSettings.roundVideoShowFps.get(),
                            true
                    );
                } else {
                    cell.setTextAndCheck(
                            LocaleController.getString(R.string.RoundVideoCompositionEnabled),
                            SharedSettings.roundVideoComposition.get(),
                            false
                    );
                }
            } else if (holder.getItemViewType() == TYPE_VALUE) {
                TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                cell.setEnabled(enabled || position == ROW_DIAGNOSTICS);
                if (position == ROW_OUTPUT_RESOLUTION) {
                    cell.setTextAndValue(
                            LocaleController.getString(R.string.RoundVideoOutputResolution),
                            SharedSettings.roundVideoOutputResolution.get().getSize() + "p",
                            true
                    );
                } else if (position == ROW_CAMERA_RESOLUTION) {
                    cell.setTextAndValue(
                            LocaleController.getString(R.string.RoundVideoCameraResolution),
                            cameraResolutionLabel(SharedSettings.roundVideoCameraResolution.get()),
                            true
                    );
                } else if (position == ROW_FRAME_RATE) {
                    cell.setTextAndValue(
                            LocaleController.getString(R.string.RoundVideoFrameRate),
                            frameRateLabel(SharedSettings.getRequestedRoundVideoFrameRate()),
                            true
                    );
                } else if (position == ROW_DIAGNOSTICS) {
                    cell.setTextAndValue(
                            LocaleController.getString(R.string.RoundVideoDiagnostics),
                            LocaleController.getString(R.string.RoundVideoDiagnosticsValue),
                            false
                    );
                } else {
                    cell.setTextAndValue(
                            LocaleController.getString(R.string.RoundVideoBitrate),
                            formatBitrate(SharedSettings.roundVideoVideoBitrate.get()),
                            false
                    );
                }
            } else {
                TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                cell.setText(position == ROW_GENERAL_INFO
                        ? LocaleController.getString(R.string.RoundVideoGeneralInfo)
                        : LocaleController.getString(R.string.RoundVideoCompositionInfo));
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == ROW_GENERAL_HEADER || position == ROW_COMPOSITION_HEADER) {
                return TYPE_HEADER;
            }
            if (position == ROW_ENABLED
                    || position == ROW_SHOW_FPS
                    || position == ROW_COMPOSITION) {
                return TYPE_CHECK;
            }
            if (position == ROW_GENERAL_INFO || position == ROW_COMPOSITION_INFO) {
                return TYPE_INFO;
            }
            return TYPE_VALUE;
        }
    }
}
