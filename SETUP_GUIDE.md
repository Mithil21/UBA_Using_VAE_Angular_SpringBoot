# UBA-VAE — Hyperledger Fabric Peer Lifecycle Guide

This file exists to hold one specific piece of detail: the complete chaincode
install/approve/commit sequence for the `auditcontract` chaincode, which the
main [README.md](./README.md) (Step 4, "Hyperledger Fabric") intentionally
summarised and pointed here for.

**Everything else — prerequisites, PostgreSQL setup, training the VAE, running
the five services, testing bot detection, the dashboard — lives in
[README.md](./README.md).** This file only covers the Fabric chaincode
lifecycle, since that part has enough moving pieces (two organisations, a
package ID that only exists after install, CCAAS server startup ordering) to
deserve its own walkthrough.

Run everything below from `~/fabric-samples/test-network`, after
`./network.sh up createChannel -c auditchannel -s couchdb` has already
succeeded and the `auditcontract-ccaas.tar.gz` package has already been built
(both covered in the README).

---

## 1. Install the chaincode package on Org1

```bash
cd ~/fabric-samples/test-network

export CORE_PEER_TLS_ENABLED=true
export CORE_PEER_LOCALMSPID="Org1MSP"
export CORE_PEER_TLS_ROOTCERT_FILE=${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt
export CORE_PEER_MSPCONFIGPATH=${PWD}/organizations/peerOrganizations/org1.example.com/users/Admin@org1.example.com/msp
export CORE_PEER_ADDRESS=localhost:7051

peer lifecycle chaincode install ~/fabric-samples/audit-chaincode/auditcontract-ccaas.tar.gz
```

## 2. Get the package ID

The install step above does not print a usable variable directly — query for it:

```bash
peer lifecycle chaincode queryinstalled
```

This prints something like:

```
Installed chaincodes on peer:
Package ID: auditcontract_1.0:9b8f2c4a1e7d..., Label: auditcontract_1.0
```

Copy the full `Package ID` value (everything after `Package ID: `, including
the hash after the colon) and export it:

```bash
export PACKAGE_ID=auditcontract_1.0:9b8f2c4a1e7d...   # use your actual value
```

## 3. Start the CCAAS chaincode server now, before approving

This is the step that's easy to get in the wrong order. The peers will try to
reach the chaincode server as soon as a transaction needs it — approving and
committing don't strictly require it running yet, but querying or invoking
afterwards will hang or fail if it isn't. Start it now, in its own terminal,
so it's already listening by the time you test anything:

```bash
cd ~/fabric-samples/audit-chaincode

CHAINCODE_SERVER_ADDRESS=0.0.0.0:9999 \
CORE_CHAINCODE_ID_NAME=$PACKAGE_ID \
./auditcontract-server
```

Leave this running. Go back to a separate terminal (with the Org1 environment
variables from Step 1 still exported) for the rest of this guide.

## 4. Approve the chaincode definition for Org1

```bash
export ORDERER_CA=${PWD}/organizations/ordererOrganizations/example.com/orderers/orderer.example.com/msp/tlscacerts/tlsca.example.com-cert.pem

peer lifecycle chaincode approveformyorg -o localhost:7050 \
  --ordererTLSHostnameOverride orderer.example.com \
  --tls --cafile "$ORDERER_CA" \
  --channelID auditchannel --name auditcontract --version 1.0 \
  --package-id $PACKAGE_ID --sequence 1
```

## 5. Repeat install + approve for Org2

Switch the environment variables to Org2, then repeat the same install and
approve commands with Org2's context:

```bash
export CORE_PEER_LOCALMSPID="Org2MSP"
export CORE_PEER_TLS_ROOTCERT_FILE=${PWD}/organizations/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt
export CORE_PEER_MSPCONFIGPATH=${PWD}/organizations/peerOrganizations/org2.example.com/users/Admin@org2.example.com/msp
export CORE_PEER_ADDRESS=localhost:9051

peer lifecycle chaincode install ~/fabric-samples/audit-chaincode/auditcontract-ccaas.tar.gz
# Package ID will be identical to Org1's, since it's the same .tar.gz

peer lifecycle chaincode approveformyorg -o localhost:7050 \
  --ordererTLSHostnameOverride orderer.example.com \
  --tls --cafile "$ORDERER_CA" \
  --channelID auditchannel --name auditcontract --version 1.0 \
  --package-id $PACKAGE_ID --sequence 1
```

Both organisations need to approve independently — this is what "two
organisations must endorse every transaction" (described in the README's
architecture section) actually looks like at setup time, not just at
transaction time.

## 6. Check commit readiness

```bash
peer lifecycle chaincode checkcommitreadiness \
  --channelID auditchannel --name auditcontract --version 1.0 --sequence 1 \
  --tls --cafile "$ORDERER_CA" --output json
```

Expect both `Org1MSP` and `Org2MSP` to show `true`. If either shows `false`,
that organisation's approval in Step 4/5 didn't go through — redo it before
continuing.

## 7. Commit the chaincode definition to the channel

This step needs both organisations' peer addresses and TLS certs at once,
regardless of which org's environment variables are currently active:

```bash
peer lifecycle chaincode commit -o localhost:7050 \
  --ordererTLSHostnameOverride orderer.example.com \
  --tls --cafile "$ORDERER_CA" \
  --channelID auditchannel --name auditcontract --version 1.0 --sequence 1 \
  --peerAddresses localhost:7051 \
  --tlsRootCertFiles ${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt \
  --peerAddresses localhost:9051 \
  --tlsRootCertFiles ${PWD}/organizations/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt
```

## 8. Verify it committed

```bash
peer lifecycle chaincode querycommitted --channelID auditchannel --name auditcontract
```

## 9. Sanity-check the chaincode directly (optional, but worth doing once)

Before starting the Spring Boot backend, confirm the chaincode itself works,
independent of the Java application. Using the signature documented in the
main README (`CommitRecord(uuid, email, decision, hash, vaeScore, mseScore)`
and `VerifyRecord(uuid)`):

```bash
peer chaincode invoke -o localhost:7050 \
  --ordererTLSHostnameOverride orderer.example.com \
  --tls --cafile "$ORDERER_CA" \
  -C auditchannel -n auditcontract \
  --peerAddresses localhost:7051 \
  --tlsRootCertFiles ${PWD}/organizations/peerOrganizations/org1.example.com/peers/peer0.org1.example.com/tls/ca.crt \
  --peerAddresses localhost:9051 \
  --tlsRootCertFiles ${PWD}/organizations/peerOrganizations/org2.example.com/peers/peer0.org2.example.com/tls/ca.crt \
  -c '{"function":"CommitRecord","Args":["test-uuid-001","test@example.com","ACCEPTED","dummyhash123","0.95","1.2"]}'

peer chaincode query -C auditchannel -n auditcontract \
  -c '{"function":"VerifyRecord","Args":["test-uuid-001"]}'
```

The query should return the hash you just committed. If this works, the
ledger side is solid — any Fabric-related issue after this point is in the
Spring Boot `FabricService`/`FabricGatewayConfig` connection layer, not the
chaincode itself.

> **Note:** double-check the argument names, order, and types above against
> your actual `audit_contract.go` if you've changed the function signature
> since writing this guide — this reflects what's documented in the README,
> not a live read of the chaincode source.

---

## Troubleshooting this part specifically

**`checkcommitreadiness` shows `false` for one org** — that org's
`approveformyorg` (Step 4 or 5) either didn't run or targeted the wrong peer.
Re-export that org's environment variables and re-run the approve command.

**`commit` fails with an endorsement error** — almost always means the CCAAS
server (Step 3) isn't running, or `CORE_CHAINCODE_ID_NAME` doesn't exactly
match the `Package ID` from Step 2 (it must include everything after
`Package ID: `, not just the label).

**Peer commands hang with no output** — check the CCAAS server terminal for a
connection attempt; if there's nothing, the peer likely can't reach
`host.docker.internal:9999` from inside its container. Confirm the
`connection.json` inside the `.tar.gz` package (built in the README's Step 4)
actually points at `host.docker.internal:9999` and that Docker Desktop's
`vm.docker.internal` resolution is enabled.

**Re-running after a mistake** — if you need to redo an approval, you cannot
reuse `--sequence 1` for the same chaincode name once it's already been
approved by both orgs with that sequence; increment `--sequence` (2, 3, ...)
consistently across every command above and repeat from Step 4.
