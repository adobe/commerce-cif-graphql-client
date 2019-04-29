# GraphQL client

This project is a GraphQL client for AEM. It is an OSGi bundle that can be instantiated with an OSGi configuration in the AEM OSGi configuration console. It can also be instantiated directly with java code.

## OSGi configuration

To instantiate instances of this GraphQL client, simply go the AEM OSGi configuration console and look for "GraphQL Client Configuration Factory". Add a configuration and set the following mandatory parameters:
* `identifier`: must be unique among all GraphQL clients.
* `url`: the URL of the GraphQL server endpoint used by this client.

The `identifier` is used by the adapter factory to resolve clients via the `cq:graphqlClient` property set on any JCR node. When this is set on a resource or the resource ancestors, one can write `GraphqlClient client = resource.adaptTo(GraphqlClient.class);`.
