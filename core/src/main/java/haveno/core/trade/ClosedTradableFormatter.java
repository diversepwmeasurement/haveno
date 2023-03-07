/*
 * This file is part of Haveno.
 *
 * Haveno is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Haveno is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Haveno. If not, see <http://www.gnu.org/licenses/>.
 */

package haveno.core.trade;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Monetary;
import org.bitcoinj.utils.Fiat;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import static haveno.core.trade.ClosedTradableUtil.*;
import static haveno.core.trade.Trade.DisputeState.DISPUTE_CLOSED;
import static haveno.core.trade.Trade.DisputeState.MEDIATION_CLOSED;
import static haveno.core.trade.Trade.DisputeState.REFUND_REQUEST_CLOSED;
import static haveno.core.util.FormattingUtils.BTC_FORMATTER_KEY;
import static haveno.core.util.FormattingUtils.formatPercentagePrice;
import static haveno.core.util.FormattingUtils.formatToPercentWithSymbol;
import static haveno.core.util.VolumeUtil.formatVolume;
import static haveno.core.util.VolumeUtil.formatVolumeWithCode;

import haveno.core.locale.CurrencyUtil;
import haveno.core.locale.Res;
import haveno.core.monetary.Altcoin;
import haveno.core.monetary.Volume;
import haveno.core.offer.OpenOffer;
import haveno.core.util.FormattingUtils;
import haveno.core.util.coin.CoinFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class ClosedTradableFormatter {
    // Resource bundle i18n keys with Desktop UI specific property names,
    // having "generic-enough" property values to be referenced in the core layer.
    private static final String I18N_KEY_TOTAL_AMOUNT = "closedTradesSummaryWindow.totalAmount.value";
    private static final String I18N_KEY_TOTAL_TX_FEE = "closedTradesSummaryWindow.totalMinerFee.value";
    private static final String I18N_KEY_TOTAL_TRADE_FEE_BTC = "closedTradesSummaryWindow.totalTradeFeeInBtc.value";

    private final CoinFormatter btcFormatter;
    private final ClosedTradableManager closedTradableManager;

    @Inject
    public ClosedTradableFormatter(ClosedTradableManager closedTradableManager,
                                   @Named(BTC_FORMATTER_KEY) CoinFormatter btcFormatter) {
        this.closedTradableManager = closedTradableManager;
        this.btcFormatter = btcFormatter;
    }

    public String getAmountAsString(Tradable tradable) {
        return tradable.getOptionalAmount().map(HavenoUtils::formatToXmr).orElse("");
    }

    public String getTotalAmountWithVolumeAsString(Coin totalTradeAmount, Volume volume) {
        return Res.get(I18N_KEY_TOTAL_AMOUNT,
                btcFormatter.formatCoin(totalTradeAmount, true),
                formatVolumeWithCode(volume));
    }

    public String getTxFeeAsString(Tradable tradable) {
        return btcFormatter.formatCoin(getTxFee(tradable));
    }

    public String getTotalTxFeeAsString(Coin totalTradeAmount, Coin totalTxFee) {
        double percentage = ((double) totalTxFee.value) / totalTradeAmount.value;
        return Res.get(I18N_KEY_TOTAL_TX_FEE,
                btcFormatter.formatCoin(totalTxFee, true),
                formatToPercentWithSymbol(percentage));
    }

    public String getBuyerSecurityDepositAsString(Tradable tradable) {
        return HavenoUtils.formatToXmr(tradable.getOffer().getBuyerSecurityDeposit());
    }

    public String getSellerSecurityDepositAsString(Tradable tradable) {
        return HavenoUtils.formatToXmr(tradable.getOffer().getSellerSecurityDeposit());
    }

    public String getTradeFeeAsString(Tradable tradable, boolean appendCode) {
        closedTradableManager.getBtcTradeFee(tradable);
        return btcFormatter.formatCoin(Coin.valueOf(closedTradableManager.getBtcTradeFee(tradable)), appendCode);
    }

    public String getTotalTradeFeeInBtcAsString(Coin totalTradeAmount, Coin totalTradeFee) {
        double percentage = ((double) totalTradeFee.value) / totalTradeAmount.value;
        return Res.get(I18N_KEY_TOTAL_TRADE_FEE_BTC,
                btcFormatter.formatCoin(totalTradeFee, true),
                formatToPercentWithSymbol(percentage));
    }

    public String getPriceDeviationAsString(Tradable tradable) {
        if (tradable.getOffer().isUseMarketBasedPrice()) {
            return formatPercentagePrice(tradable.getOffer().getMarketPriceMarginPct());
        } else {
            return Res.get("shared.na");
        }
    }

    public String getVolumeAsString(Tradable tradable, boolean appendCode) {
        return tradable.getOptionalVolume().map(volume -> formatVolume(volume, appendCode)).orElse("");
    }

    public String getVolumeCurrencyAsString(Tradable tradable) {
        return tradable.getOptionalVolume().map(Volume::getCurrencyCode).orElse("");
    }

    public String getPriceAsString(Tradable tradable) {
        return tradable.getOptionalPrice().map(FormattingUtils::formatPrice).orElse("");
    }

    public Map<String, String> getTotalVolumeByCurrencyAsString(List<Tradable> tradableList) {
        return getTotalVolumeByCurrency(tradableList).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> {
                            String currencyCode = entry.getKey();
                            Monetary monetary;
                            if (CurrencyUtil.isCryptoCurrency(currencyCode)) {
                                monetary = Altcoin.valueOf(currencyCode, entry.getValue());
                            } else {
                                monetary = Fiat.valueOf(currencyCode, entry.getValue());
                            }
                            return formatVolumeWithCode(new Volume(monetary));
                        }
                ));
    }

    public String getStateAsString(Tradable tradable) {
        if (tradable == null) {
            return "";
        }

        if (isHavenoV1Trade(tradable)) {
            Trade trade = castToTrade(tradable);
            if (trade.isCompleted() || trade.isPayoutPublished()) {
                return Res.get("portfolio.closed.completed");
            } else if (trade.getDisputeState() == DISPUTE_CLOSED) {
                return Res.get("portfolio.closed.ticketClosed");
            } else if (trade.getDisputeState() == MEDIATION_CLOSED) {
                return Res.get("portfolio.closed.mediationTicketClosed");
            } else if (trade.getDisputeState() == REFUND_REQUEST_CLOSED) {
                return Res.get("portfolio.closed.ticketClosed");
            } else {
                log.error("That must not happen. We got a pending state but we are in"
                                + " the closed trades list. state={}",
                        trade.getState().name());
                return Res.get("shared.na");
            }
        } else if (isOpenOffer(tradable)) {
            OpenOffer.State state = ((OpenOffer) tradable).getState();
            log.trace("OpenOffer state={}", state);
            switch (state) {
                case AVAILABLE:
                case RESERVED:
                case CLOSED:
                case DEACTIVATED:
                    log.error("Invalid state {}", state);
                    return state.name();
                case CANCELED:
                    return Res.get("portfolio.closed.canceled");
                default:
                    log.error("Unhandled state {}", state);
                    return state.name();
            }
        }
        return Res.get("shared.na");
    }
}