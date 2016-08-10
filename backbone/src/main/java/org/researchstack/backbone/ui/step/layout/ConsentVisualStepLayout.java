package org.researchstack.backbone.ui.step.layout;

import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.jakewharton.rxbinding.view.RxView;
import rx.functions.Action1;

import org.apache.commons.io.FilenameUtils;
import org.researchstack.backbone.R;
import org.researchstack.backbone.ResourcePathManager;
import org.researchstack.backbone.model.ConsentSection;
import org.researchstack.backbone.result.StepResult;
import org.researchstack.backbone.step.ConsentVisualStep;
import org.researchstack.backbone.step.Step;
import org.researchstack.backbone.ui.ViewWebDocumentActivity;
import org.researchstack.backbone.ui.callbacks.StepCallbacks;
import org.researchstack.backbone.ui.views.FixedSubmitBarLayout;
import org.researchstack.backbone.ui.views.SubmitBar;
import org.researchstack.backbone.utils.LogExt;
import org.researchstack.backbone.utils.ResUtils;
import org.researchstack.backbone.utils.TextUtils;

import java.io.File;

public class ConsentVisualStepLayout extends FixedSubmitBarLayout implements StepLayout {

    private StepCallbacks callbacks;
    private ConsentVisualStep step;

    public ConsentVisualStepLayout(Context context) {
        super(context);
    }

    public ConsentVisualStepLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ConsentVisualStepLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public void initialize(Step step, StepResult result) {
        this.step = (ConsentVisualStep) step;
        initializeStep();
    }

    @Override
    public View getLayout() {
        return this;
    }

    @Override
    public boolean isBackEventConsumed() {
        callbacks.onSaveStep(StepCallbacks.ACTION_PREV, step, null);
        return false;
    }

    @Override
    public void setCallbacks(StepCallbacks callbacks) {
        this.callbacks = callbacks;
    }

    @Override
    public void receiveIntentExtraOnResult(int requestCode, Intent intent) {

    }

    @Override
    public int getContentResourceId() {
        return R.layout.rsb_step_layout_consent_visual;
    }

    private void initializeStep() {
        ConsentSection data = step.getSection();

        // Set Image
        TypedValue typedValue = new TypedValue();
        TypedArray a = getContext().obtainStyledAttributes(typedValue.data,
                new int[]{R.attr.colorAccent});
        int accentColor = a.getColor(0, 0);
        a.recycle();

        ImageView imageView = (ImageView) findViewById(R.id.image);

        String imageName = !TextUtils.isEmpty(data.getCustomImageName())
                ? data.getCustomImageName()
                : data.getType().getImageName();

        int imageResId = ResUtils.getDrawableResourceId(getContext(), imageName);

        if (imageResId != 0) {
            Drawable drawable = getResources().getDrawable(imageResId);
            drawable = DrawableCompat.wrap(drawable);
            DrawableCompat.setTint(drawable, accentColor);
            imageView.setImageDrawable(drawable);
            imageView.setVisibility(View.VISIBLE);
        } else {
            imageView.setVisibility(View.GONE);
        }

        // Set Title
        TextView titleView = (TextView) findViewById(R.id.title);
        String title = TextUtils.isEmpty(data.getTitle()) ? getResources().getString(data.getType()
                .getTitleResId()) : data.getTitle();
        titleView.setText(title);

        // Set Summary
        TextView summaryView = (TextView) findViewById(R.id.summary);
        summaryView.setText(data.getSummary());

        // Set more info
        TextView moreInfoView = (TextView) findViewById(R.id.more_info);

        if (!TextUtils.isEmpty(data.getHtmlContent())) {
            if (!TextUtils.isEmpty(data.getCustomLearnMoreButtonTitle())) {
                moreInfoView.setText(data.getCustomLearnMoreButtonTitle());
            } else {
                moreInfoView.setText(data.getType().getMoreInfoResId());
            }

            RxView.clicks(moreInfoView).subscribe(v -> {
                String webTitle = getResources().getString(R.string.rsb_consent_section_more_info);
                Intent webDoc = ViewWebDocumentActivity.newIntentForContent(getContext(), webTitle,
                        TextUtils.isEmpty(data.getContent()) ? data.getHtmlContent() : data.getContent());
                getContext().startActivity(webDoc);
            });
        } else {
            moreInfoView.setVisibility(View.GONE);
        }

        final SubmitBar submitBar = (SubmitBar) findViewById(R.id.rsb_submit_bar);
        submitBar.setPositiveTitle(step.getNextButtonString());
        submitBar.setPositiveAction(positiveAction());
        submitBar.getNegativeActionView().setVisibility(View.GONE);

        Button emailButton = (Button) findViewById(R.id.email_doc);
        if (!TextUtils.isEmpty(data.getShareContent())) {
            LogExt.i(getClass(), "Consent Section had sectionShareContent " + data.getShareContent());
            emailButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent emailIntent = new Intent(Intent.ACTION_SEND);
                    emailIntent.setType("application/pdf");
                    emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    emailIntent.putExtra(Intent.EXTRA_SUBJECT, data.getTitle());

                    File externalCopy;
                    try {
                        int type = ResourcePathManager.getInstance().getFileType(FilenameUtils.getExtension(data.getShareContent()));
                        String base = FilenameUtils.getBaseName(data.getShareContent());

                        externalCopy = ResourcePathManager.getInstance().saveResourceToExternalStorage(getContext(), new ResourcePathManager.Resource(type, "", base));
                        Uri uri = Uri.fromFile(externalCopy);
                        emailIntent.putExtra(Intent.EXTRA_STREAM, uri);
                    } catch (Exception e) {
                        LogExt.e(this.getClass(), "Could not write resource to external storage");
                        e.printStackTrace();
                    }

                    getContext().startActivity(Intent.createChooser(emailIntent, "Send email..."));
                }
            });
        } else {
            emailButton.setVisibility(View.GONE);
        }

        CheckBox checkAcceptance = (CheckBox) findViewById(R.id.accept_consent_section);

        if (!TextUtils.isEmpty(data.getAcceptanceText())) {
            LogExt.i(getClass(), "Section had acceptance text");
            submitBar.setPositiveAction(positiveAction(false));

            checkAcceptance.setVisibility(VISIBLE);
            checkAcceptance.setText(data.getAcceptanceText());

            checkAcceptance.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    submitBar.setPositiveAction(positiveAction(isChecked));
                }
            });
        } else {
            checkAcceptance.setVisibility(View.GONE);
        }

    }
    
    private Action1 positiveAction() {
        return v -> callbacks.onSaveStep(StepCallbacks.ACTION_NEXT,
                step,
                null);
    }
    
    private Action1 positiveAction(Boolean accepted) {
        if (accepted) {
            return positiveAction();
        } else {
            return v -> Toast.makeText(getContext(),
                    R.string.rsb_consent_step_acceptance,
                    Toast.LENGTH_SHORT).show();
        }
    }
}
