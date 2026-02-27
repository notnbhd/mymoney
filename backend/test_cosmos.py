"""Quick diagnostic to find the exact Cosmos DB error."""
import traceback
import logging

# Suppress verbose Azure HTTP logs
logging.getLogger("azure.core.pipeline.policies.http_logging_policy").setLevel(logging.WARNING)

try:
    from azure.cosmos import CosmosClient, PartitionKey, exceptions
    from config import settings

    print(f"Endpoint: {settings.COSMOS_DB_ENDPOINT}")
    print(f"Database: {settings.COSMOS_DB_DATABASE}")
    print(f"Container: {settings.COSMOS_DB_CONTAINER}")
    print()

    # Step 1: Connect
    print("[1] Connecting to Cosmos DB...")
    client = CosmosClient(settings.COSMOS_DB_ENDPOINT, credential=settings.COSMOS_DB_KEY)
    print("    OK - Client created")

    # Step 2: Database
    print("[2] Getting database...")
    database = client.create_database_if_not_exists(id=settings.COSMOS_DB_DATABASE)
    print("    OK - Database ready")

    # Step 3: List existing containers
    print("[3] Listing containers...")
    containers = list(database.list_containers())
    for c in containers:
        print(f"    Found: {c['id']}")
    if not containers:
        print("    (no containers found)")

    # Step 4: Create vector container
    print("[4] Creating vector-search container...")
    vector_policy = {
        "vectorEmbeddings": [{
            "path": "/embedding",
            "dataType": "float32",
            "distanceFunction": "cosine",
            "dimensions": 384,
        }]
    }
    index_policy = {
        "indexingMode": "consistent",
        "includedPaths": [{"path": "/*"}],
        "excludedPaths": [{"path": "/_etag/?"}],
        "vectorIndexes": [{"path": "/embedding", "type": "quantizedFlat"}],
    }

    try:
        database.delete_container(settings.COSMOS_DB_CONTAINER)
        print(f"    Deleted existing '{settings.COSMOS_DB_CONTAINER}'")
    except exceptions.CosmosResourceNotFoundError:
        print(f"    Container '{settings.COSMOS_DB_CONTAINER}' does not exist yet")

    container = database.create_container(
        id=settings.COSMOS_DB_CONTAINER,
        partition_key=PartitionKey(path="/category"),
        vector_embedding_policy=vector_policy,
        indexing_policy=index_policy,
        offer_throughput=400,
    )
    print("    OK - Container created with vector search!")

    # Step 5: Test insert
    print("[5] Testing document insert...")
    test_doc = {
        "id": "test_001",
        "category": "test",
        "topic": "Test Document",
        "embedding": [0.1] * 384,
    }
    container.upsert_item(test_doc)
    print("    OK - Test document inserted")

    # Step 6: Cleanup test doc
    container.delete_item(item="test_001", partition_key="test")
    print("    OK - Test document cleaned up")

    print("\n✅ All checks passed! Cosmos DB is ready.")

except Exception as e:
    print(f"\n❌ ERROR at step above: {type(e).__name__}")
    print(f"   Message: {str(e)[:1500]}")
    print()
    traceback.print_exc()
