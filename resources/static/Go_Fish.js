const ws = new WebSocket("ws://localhost:8080/gofish")
let isHost = false
ws.onmessage = (e)=>{
const msg = JSON.parse(e.data)

switch(msg.type){

case "ROOM_CREATED":
isHost = msg.host
document.getElementById("roomInfo").innerText="Room ID: "+msg.roomId
if(isHost) document.getElementById("startBtn").style.display="block"
break

case "PLAYER_UPDATE":
document.getElementById("players").innerText="Players: "+msg.count+"/4"
break

case "START":
document.getElementById("lobby").style.display="none"
document.getElementById("game").style.display="block"
renderHand(msg.cards)
break

case "ASK_RESULT":
alert(msg.success ? "Got cards!" : "Go Fish!")
break

case "GAME_END":
document.getElementById("game").style.display="none"
document.getElementById("end").style.display="block"
document.getElementById("result").innerText =
    msg.winner == 0 ? "You Win!" : "Player "+msg.winner+" Wins!"
break
}
}

function createRoom(){
ws.send(JSON.stringify({type:"CREATE"}))
}

function joinRoom(){
const id=document.getElementById("roomId").value
ws.send(JSON.stringify({
type:"JOIN",
roomId:id
}))
}

function startGame(){
ws.send(JSON.stringify({type:"START"}))
}

function renderHand(cards){
const div=document.getElementById("hand")
div.innerHTML=""
cards.forEach((c,i)=>{
const img=document.createElement("img")
img.src=c
img.width=90
img.onclick=()=>{
ws.send(JSON.stringify({
type:"ASK",
target:0,
rank:extractRank(c)
}))
}

div.appendChild(img)
})
}

function extractRank(url){
if(url.includes("ace")) return "A"
if(url.includes("jack")) return "Jack"
if(url.includes("queen")) return "Queen"
if(url.includes("king")) return "King"
return url.match(/(\d+)_of/)[1]
}