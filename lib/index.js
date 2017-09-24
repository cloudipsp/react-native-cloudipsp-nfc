import {
    Platform,
    NativeModules
} from 'react-native';

import {Card, Order} from 'react-native-cloudipsp';

const nat = NativeModules.CloudipspNfc;

function promiseRejectAsUnSupported() {
    return Promise.reject('UnSupported OS');
}

export class Bridge {
    getCard = (order:Order) => {
        if (!order) {
            throw new Error('Parameter "order" is required');
        }
        if (!this.__card__) {
            return null;
        }
        order.addArgument('kkh', nat.kkh());
        const card = this.__card__;
        this.__card__ = undefined;
        return card;
    }

    displayCard = (cardForm) => {
        if (!cardForm) {
            throw new Error('Parameter "cardForm" is required');
        }
        if (this.__card__) {
            cardForm.showCard(this.__card__);
        }
    }
}

export function isSupporting(): Promise<Boolean> {
    if (Platform.OS === 'android') {
        return nat.isSupporting();
    } else {
        return promiseRejectAsUnSupported();
    }
}

export function isEnabled(): Promise<Boolean> {
    if (Platform.OS === 'android') {
        return nat.isEnabled();
    } else {
        return promiseRejectAsUnSupported();
    }
}

export function enable(): Promise {
    if (Platform.OS === 'android') {
        return nat.enable();
    } else {
        return promiseRejectAsUnSupported();
    }
}

export function subscribe(): Promise<Bridge> {
    if (Platform.OS === 'android') {
        return nat.subscribe()
            .then((cardInfo) => {
                const card = new Card();
                card.getSource = () => 'nfc';
                card.__getCardNumber__ = () => cardInfo.cardNumber;
                card.__getExpYy__ = () => cardInfo.expYy;
                card.__getExpMm__ = () => cardInfo.expMm;
                card.isValidCvv = () => true;

                const bridge = new Bridge();
                bridge.__card__ = card;
                return bridge;
            });
    } else {
        return promiseRejectAsUnSupported();
    }
}