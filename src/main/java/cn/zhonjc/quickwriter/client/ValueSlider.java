package cn.zhonjc.quickwriter.client;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

import java.util.function.DoubleConsumer;
import java.util.function.DoubleFunction;
import java.util.function.DoubleUnaryOperator;

/** Slider over an arbitrary value range: {@code toValue} maps slider position 0..1 to the (rounded) value. */
public class ValueSlider extends AbstractSliderButton {
    private final DoubleUnaryOperator toValue;
    private final DoubleFunction<Component> label;
    private final DoubleConsumer onChange;

    public ValueSlider(int x, int y, int w, int h, double position, DoubleUnaryOperator toValue,
                       DoubleFunction<Component> label, DoubleConsumer onChange) {
        super(x, y, w, h, Component.empty(), position);
        this.toValue = toValue;
        this.label = label;
        this.onChange = onChange;
        updateMessage();
    }

    private double current() { return toValue.applyAsDouble(value); }

    @Override protected void updateMessage() { setMessage(label.apply(current())); }

    @Override protected void applyValue() { onChange.accept(current()); }
}
