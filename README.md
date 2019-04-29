# GraphQL client

This project is a GraphQL client for AEM. It is an OSGi bundle that can be instantiated with an OSGi configuration in the AEM OSGi configuration console. It can also be instantiated directly with java code.

## Using the GraphQL client

To use this library in your project, just add the following maven dependency to your project and install the bundle in your AEM instance:

```xml
<dependency>
    <groupId>com.adobe.commerce.cif</groupId>
    <artifactId>graphql-client</artifactId>
    <version>...</version>
    <scope>provided</scope>
</dependency>
```

You'll then have to setup and configure the client in your AEM instance.

## OSGi configuration

To instantiate instances of this GraphQL client, simply go the AEM OSGi configuration console and look for "GraphQL Client Configuration Factory". Add a configuration and set the following mandatory parameters:
* `identifier`: must be unique among all GraphQL clients.
* `url`: the URL of the GraphQL server endpoint used by this client.

The `identifier` is used by the adapter factory to resolve clients via the `cq:graphqlClient` property set on any JCR node. When this is set on a resource or the resource ancestors, one can write `GraphqlClient client = resource.adaptTo(GraphqlClient.class);`.

### Contributing
 
Contributions are welcomed! Read the [Contributing Guide](.github/CONTRIBUTING.md) for more information.
 
### Licensing
 
This project is licensed under the Apache V2 License. See [LICENSE](LICENSE) for more information.
