package com.mouse.backend.util;

import org.bitcoinj.base.Address;
import org.bitcoinj.script.Script;

public record AddressScript(Address address, Script script) {

}
