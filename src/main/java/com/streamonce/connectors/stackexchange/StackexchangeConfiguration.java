package com.streamonce.connectors.stackexchange;

import com.streamonce.sdk.v1.connector.config.ConfigurationProvider;
import com.streamonce.sdk.v1.connector.config.types.Input;
import com.streamonce.sdk.v1.connector.config.types.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: yuval.twig
 * Date: 20/05/2014
 * Time: 12:06
 * To change this template use File | Settings | File Templates.
 */
public class StackexchangeConfiguration implements ConfigurationProvider {

    public List<Input> getConfigurations() {
        List<Input> userInputs = new ArrayList<>(1);

        userInputs.add(Text.withName("Tag").andDescription("Tag to follow").andPlaceholder("Stackoverflow tag"));


        return userInputs;

    }
}
